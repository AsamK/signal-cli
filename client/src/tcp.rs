use jsonrpc_client_transports::{transports::duplex, RpcChannel, RpcError};
use jsonrpc_core::futures_util::{SinkExt, StreamExt, TryStreamExt};
use jsonrpc_server_utils::{codecs::StreamCodec, tokio_util::codec::Decoder};
use tokio::net::{TcpStream, ToSocketAddrs};

/// Connect to a JSON-RPC TCP server.
pub async fn connect<S: ToSocketAddrs, Client: From<RpcChannel>>(
    socket: S,
) -> Result<Client, RpcError> {
    let connection = TcpStream::connect(socket)
        .await
        .map_err(|e| RpcError::Other(Box::new(e)))?;
    let (sink, stream) = StreamCodec::stream_incoming().framed(connection).split();
    let sink = sink.sink_map_err(|e| RpcError::Other(Box::new(e)));
    let stream = stream.map_err(|e| log::error!("TCP stream error: {}", e));

    let (client, sender) = duplex(
        Box::pin(sink),
        Box::pin(
            stream
                .take_while(|x| std::future::ready(x.is_ok()))
                .map(|x| x.expect("Stream is closed upon first error.")),
        ),
    );

    tokio::spawn(client);

    Ok(sender.into())
}
