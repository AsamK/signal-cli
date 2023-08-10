use futures_util::{stream::StreamExt, Sink, SinkExt, Stream};
use jsonrpsee::core::{
    async_trait,
    client::{ReceivedMessage, TransportReceiverT, TransportSenderT},
};
use thiserror::Error;

pub mod ipc;
mod stream_codec;
pub mod tcp;

#[derive(Debug, Error)]
enum Errors {
    #[error("Other: {0}")]
    Other(String),
    #[error("Closed")]
    Closed,
}

struct Sender<T: Send + Sink<String>> {
    inner: T,
}

#[async_trait]
impl<T: Send + Sink<String, Error = impl std::error::Error> + Unpin + 'static> TransportSenderT
    for Sender<T>
{
    type Error = Errors;

    async fn send(&mut self, body: String) -> Result<(), Self::Error> {
        self.inner
            .send(body)
            .await
            .map_err(|e| Errors::Other(format!("{:?}", e)))?;
        Ok(())
    }

    async fn close(&mut self) -> Result<(), Self::Error> {
        self.inner
            .close()
            .await
            .map_err(|e| Errors::Other(format!("{:?}", e)))?;
        Ok(())
    }
}

struct Receiver<T: Send + Stream> {
    inner: T,
}

#[async_trait]
impl<T: Send + Stream<Item = Result<String, std::io::Error>> + Unpin + 'static> TransportReceiverT
    for Receiver<T>
{
    type Error = Errors;

    async fn receive(&mut self) -> Result<ReceivedMessage, Self::Error> {
        match self.inner.next().await {
            None => Err(Errors::Closed),
            Some(Ok(msg)) => Ok(ReceivedMessage::Text(msg)),
            Some(Err(e)) => Err(Errors::Other(format!("{:?}", e))),
        }
    }
}
