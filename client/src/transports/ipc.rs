use std::path::Path;

use futures_util::stream::StreamExt;
use jsonrpsee::core::client::{TransportReceiverT, TransportSenderT};
use jsonrpsee::core::Error;
use tokio::net::UnixStream;
use tokio_util::codec::Decoder;

use super::stream_codec::StreamCodec;
use super::{Receiver, Sender};

/// Connect to a JSON-RPC Unix Socket server.
pub async fn connect(
    socket: impl AsRef<Path>,
) -> Result<(impl TransportSenderT + Send, impl TransportReceiverT + Send), Error> {
    let connection = UnixStream::connect(socket).await?;
    let (sink, stream) = StreamCodec::stream_incoming().framed(connection).split();

    let sender = Sender { inner: sink };
    let receiver = Receiver { inner: stream };

    Ok((sender, receiver))
}
