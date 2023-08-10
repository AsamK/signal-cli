use bytes::BytesMut;
use std::{io, str};
use tokio_util::codec::{Decoder, Encoder};

type Separator = u8;

/// Stream codec for streaming protocols (ipc, tcp)
#[derive(Debug, Default)]
pub struct StreamCodec {
    incoming_separator: Separator,
    outgoing_separator: Separator,
}

impl StreamCodec {
    /// Default codec with streaming input data. Input can be both enveloped and not.
    pub fn stream_incoming() -> Self {
        StreamCodec::new(b'\n', b'\n')
    }

    /// New custom stream codec
    pub fn new(incoming_separator: Separator, outgoing_separator: Separator) -> Self {
        StreamCodec {
            incoming_separator,
            outgoing_separator,
        }
    }
}

impl Decoder for StreamCodec {
    type Item = String;
    type Error = io::Error;

    fn decode(&mut self, buf: &mut BytesMut) -> io::Result<Option<Self::Item>> {
        if let Some(i) = buf
            .as_ref()
            .iter()
            .position(|&b| b == self.incoming_separator)
        {
            let line = buf.split_to(i);
            let _ = buf.split_to(1);

            match str::from_utf8(line.as_ref()) {
                Ok(s) => Ok(Some(s.to_string())),
                Err(_) => Err(io::Error::new(io::ErrorKind::Other, "invalid UTF-8")),
            }
        } else {
            Ok(None)
        }
    }
}

impl Encoder<String> for StreamCodec {
    type Error = io::Error;

    fn encode(&mut self, msg: String, buf: &mut BytesMut) -> io::Result<()> {
        let mut payload = msg.into_bytes();
        payload.push(self.outgoing_separator);
        buf.extend_from_slice(&payload);
        Ok(())
    }
}
