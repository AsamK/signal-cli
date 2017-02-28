package org.asamk.signal;

import org.asamk.signal.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.messages.calls.*;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class JsonReceiveMessageHandler implements Manager.ReceiveMessageHandler {
    private static final TimeZone tzUTC = TimeZone.getTimeZone("UTC");
    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final Manager m;
    final Writer logfile;

    public JsonReceiveMessageHandler(Manager m, Writer logfile) {
        this.m = m;
        this.logfile = logfile;
        df.setTimeZone(tzUTC);
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        final JSONObject obj = new JSONObject();

        obj.put( "envelope", jsonEnvelope( envelope ) );

        if (exception != null) {
            if (exception instanceof org.whispersystems.libsignal.UntrustedIdentityException) {
                org.whispersystems.libsignal.UntrustedIdentityException e = (org.whispersystems.libsignal.UntrustedIdentityException) exception;
                final JSONObject err = new JSONObject();
                err.put("type", "UntrustedIdentityException");
                err.put("name", e.getName());
                obj.put("error", err);
            } else {
                final JSONObject err = new JSONObject();
                err.put("type", exception.getClass().getSimpleName());
                err.put("message", exception.getMessage() );
                obj.put("error", err);
            }
        }

        if (content == null) {
            if( envelope.hasContent() && exception == null ) {
                final JSONObject err = new JSONObject();
                err.put("type", exception.getClass().getSimpleName());
                err.put("message", "Failed to decrypt message");
                obj.put("error", err);
            }
        } else {
            if (content.getDataMessage().isPresent())
                obj.put( "data", jsonDataMessage( content.getDataMessage().get() ) );
            if (content.getSyncMessage().isPresent())
                obj.put( "sync", jsonSyncMessage( content.getSyncMessage().get() ) );
            if (content.getCallMessage().isPresent())
                obj.put( "call", jsonCallMessage( content.getCallMessage().get() ) );
        }

        try {
            logfile.write(obj.toString());
            logfile.write(",\n");
            logfile.flush();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static final JSONObject jsonEnvelope( SignalServiceEnvelope envelope ) {
        final JSONObject env = new JSONObject();
        env.put("timestamp", formatTimestamp(envelope.getTimestamp()));
        final JSONObject from = new JSONObject();
        from.put("number", envelope.getSource());
        from.put("device", envelope.getSourceDevice());
        SignalServiceAddress sourceAddress = envelope.getSourceAddress();
        if( sourceAddress.getRelay().isPresent() ) from.put("relay", envelope.getRelay() );
        env.put("from", from);
        final JSONObject type = new JSONObject();
        type.put("number", envelope.getType());
        if(envelope.isReceipt())                type.put("name", "receipt");
        if(envelope.isSignalMessage())          type.put("name", "message");
        if(envelope.isPreKeySignalMessage())    type.put("name", "prekey");
        env.put("type", type);
        if(envelope.hasLegacyMessage())         env.put("legacyMessage", true );
        return env;
    }

    private final JSONObject jsonAttachment(SignalServiceAttachment attachment ) {
        final JSONObject json = new JSONObject();
        json.put("content_type", attachment.getContentType() );
        json.put("type", (attachment.isPointer() ? "Pointer" : "") + (attachment.isStream() ? "Stream" : "") );
        if (attachment.isPointer()) {
            final SignalServiceAttachmentPointer pointer = attachment.asPointer();
            json.put("id", pointer.getId() );
            json.put("key", Base64.encodeBytes( pointer.getKey() ) );
            if( pointer.getRelay().isPresent() ) json.put("relay", pointer.getRelay().get() );
            if( pointer.getSize().isPresent() ) json.put("size", pointer.getSize().get());
            if( pointer.getPreview().isPresent() ) json.put("preview", Base64.encodeBytes( pointer.getPreview().get() ) );
            // Added 2017-02-25, version 2.5.2
            //if( pointer.getDigest().isPresent() ) json.put("digest", Base64.encodeBytes( pointer.getDigest().get() ) );
            final File file = m.getAttachmentFile(pointer.getId());
            if (file.exists()) json.put("file", file.toString() );
        }
        return json;
    }

    private final JSONObject jsonGroup( SignalServiceGroup ssg ) {
        final JSONObject group = new JSONObject();
        group.put( "id", Base64.encodeBytes( ssg.getGroupId() ) );
        group.put( "type", ssg.getType() );
        if( ssg.getName().isPresent() ) group.put( "name", ssg.getName().get() );
        if( ssg.getMembers().isPresent() ) {
            final JSONArray members = new JSONArray();
            for( String member : ssg.getMembers().get()) {
                members.put(member);
            }
            group.put("members", members);
        }
        if( ssg.getAvatar().isPresent() ) {
            group.put("avatar", jsonAttachment( ssg.getAvatar().get() ) );
        }
        return group;
    }

    private static final JSONArray jsonListOfStrings( List<String> membersList ) {
        final JSONArray members = new JSONArray();
        for( final String member : membersList ) {
            members.put( member );
        }
        return members;
    }

    private final JSONObject jsonDataMessage( SignalServiceDataMessage message ) {
        final JSONObject data = new JSONObject();
        data.put("timestamp", message.getTimestamp());
        if( message.getGroupInfo().isPresent() )
            data.put( "group", jsonGroup( message.getGroupInfo().get() ) );
        if( message.getBody().isPresent() )
            data.put("body", message.getBody().get());
        if( message.isEndSession() )
            data.put("isEndSession", true);
        if( message.isExpirationUpdate() )
            data.put("isExpirationUpdate", true);
        if( message.isGroupUpdate() )
            data.put("isGroupUpdate", true );
        if( message.getExpiresInSeconds() > 0)
            data.put("expiresInSeconds", message.getExpiresInSeconds());
        if( message.getAttachments().isPresent() ) {
            final JSONArray attachments = new JSONArray();
            for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                attachments.put( jsonAttachment( attachment ) );
            }
            data.put("attachments", attachments );
        }
        return data;
    }

    private final JSONObject jsonSyncMessage( SignalServiceSyncMessage syncMessage ) {
        final JSONObject sync = new JSONObject();
        if (syncMessage.getGroups().isPresent()) {
            sync.put("groups", jsonAttachment( syncMessage.getGroups().get() ) );
        }
        if (syncMessage.getContacts().isPresent()) {
            sync.put("contacts", jsonAttachment( syncMessage.getContacts().get() ) );
        }
        if (syncMessage.getRead().isPresent()) {
            final JSONArray read = new JSONArray();
            for (ReadMessage rm : syncMessage.getRead().get()) {
                final JSONObject mesg = new JSONObject();
                mesg.put("from", rm.getSender());
                mesg.put("timestamp", rm.getTimestamp());
                read.put(mesg);
            }
            sync.put("read", read);
        }
        if (syncMessage.getRequest().isPresent()) {
            final RequestMessage requestMessage = syncMessage.getRequest().get();
            final JSONObject request = new JSONObject();
            if (requestMessage.isContactsRequest()) {
                request.put("contacts", true);
            }
            if (requestMessage.isGroupsRequest()) {
                request.put("groups", true);
            }
            if (requestMessage.isBlockedListRequest()) {
                request.put("blockedNumbers", true);
            }
            sync.put("request", request);
        }
        if (syncMessage.getSent().isPresent()) {
            final JSONObject sent = new JSONObject();
            final SentTranscriptMessage sentTranscriptMessage = syncMessage.getSent().get();
            sent.put("timestamp", formatTimestamp(sentTranscriptMessage.getTimestamp()));
            if (sentTranscriptMessage.getDestination().isPresent())
                sent.put("dest", sentTranscriptMessage.getDestination().get() );
            if (sentTranscriptMessage.getExpirationStartTimestamp() > 0)
                sent.put("burntime", formatTimestamp(sentTranscriptMessage.getExpirationStartTimestamp()));
            sent.put("data", jsonDataMessage( sentTranscriptMessage.getMessage() ) );
            sync.put("sent", sent);
        }
        if (syncMessage.getBlockedList().isPresent()) {
            final JSONArray blockedNumbers = new JSONArray();
            final BlockedListMessage blockedList = syncMessage.getBlockedList().get();
            for (final String number : blockedList.getNumbers()) {
                blockedNumbers.put( number );
            }
            sync.put("blockedNumbers", blockedNumbers);
        }
        return sync;
    }

    private static final JSONObject jsonCallMessage( SignalServiceCallMessage ssCall ) {
        final JSONObject call = new JSONObject();
        if( ssCall.getOfferMessage().isPresent() ) {
            final OfferMessage offerMessage = ssCall.getOfferMessage().get();
            final JSONObject offer = new JSONObject();
            offer.put( "id", offerMessage.getId() );
            offer.put( "description", offerMessage.getDescription() );
            call.put( "offer", offer );
        }
        if( ssCall.getAnswerMessage().isPresent() ) {
            final AnswerMessage answerMessage = ssCall.getAnswerMessage().get();
            final JSONObject answer = new JSONObject();
            answer.put( "id", answerMessage.getId() );
            answer.put( "description", answerMessage.getDescription() );
            call.put( "answer", answer );
        }
        if( ssCall.getHangupMessage().isPresent() ) {
            final HangupMessage hangupMessage = ssCall.getHangupMessage().get();
            final JSONObject hangup = new JSONObject();
            hangup.put( "id", hangupMessage.getId() );
            call.put( "hangup", hangup );
        }
        if( ssCall.getBusyMessage().isPresent() ) {
            final BusyMessage busyMessage = ssCall.getBusyMessage().get();
            final JSONObject busy = new JSONObject();
            busy.put( "id", busyMessage.getId() );
            call.put( "busy", busy );
        }
        if( ssCall.getIceUpdateMessages().isPresent() ) {
            final JSONArray ices = new JSONArray();
            for( final IceUpdateMessage iceMessage : ssCall.getIceUpdateMessages().get()) {
                final JSONObject ice = new JSONObject();
                ice.put( "id", iceMessage.getId() );
                ice.put( "sdp", iceMessage.getSdp() );
                ice.put( "sdpMLineIndex", iceMessage.getSdpMLineIndex() );
                ice.put( "sdpMid", iceMessage.getSdpMid() );
                ices.put(ice);
            }
            call.put( "ices", ices );
        }
        return call;
    }

    private static final String formatTimestamp(long timestamp) {
        final Date date = new Date(timestamp);
        return df.format(date);
    }

}
