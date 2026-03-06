package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

import java.util.List;

@JsonSchema(title = "SchemaCatalog")
public class JsonSchemaCatalog {

    public JsonAdminDelete adminDelete;
    public JsonAttachment attachment;
    public JsonAttachmentData attachmentData;
    public JsonCallMessage callMessage;
    public JsonContact contact;
    public JsonContactAddress contactAddress;
    public JsonContactAvatar contactAvatar;
    public JsonContactEmail contactEmail;
    public JsonContactName contactName;
    public JsonContactPhone contactPhone;
    public JsonDataMessage dataMessage;
    public JsonEditMessage editMessage;
    public JsonError error;
    public JsonGroupInfo groupInfo;
    public JsonMention mention;
    public JsonMessageEnvelope messageEnvelope;
    public JsonPayment payment;
    public JsonPinMessage pinMessage;
    public JsonPollCreate pollCreate;
    public JsonPollTerminate pollTerminate;
    public JsonPollVote pollVote;
    public JsonPreview preview;
    public JsonQuote quote;
    public JsonQuotedAttachment quotedAttachment;
    public JsonReaction reaction;
    public JsonReceiptMessage receiptMessage;
    public JsonRecipientAddress recipientAddress;
    public JsonRemoteDelete remoteDelete;
    public JsonSendMessageResult sendMessageResult;
    public JsonSharedContact sharedContact;
    public JsonSticker sticker;
    public JsonStoryContext storyContext;
    public JsonStoryMessage storyMessage;
    public JsonSyncDataMessage syncDataMessage;
    public JsonSyncMessage syncMessage;
    public JsonSyncReadMessage syncReadMessage;
    public JsonSyncStoryMessage syncStoryMessage;
    public JsonTextStyle textStyle;
    public JsonTypingMessage typingMessage;
    public JsonUnpinMessage unpinMessage;

    public List<JsonAttachment> attachments;
    public List<JsonMention> mentions;
    public List<JsonPreview> previews;
    public List<JsonSharedContact> contacts;
    public List<JsonTextStyle> textStyles;
}
