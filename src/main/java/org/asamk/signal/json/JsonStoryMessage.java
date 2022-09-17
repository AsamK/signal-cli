package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.Color;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.groups.GroupId;

import java.util.List;

record JsonStoryMessage(
        boolean allowsReplies,
        @JsonInclude(JsonInclude.Include.NON_NULL) String groupId,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonAttachment fileAttachment,
        @JsonInclude(JsonInclude.Include.NON_NULL) TextAttachment textAttachment
) {

    static JsonStoryMessage from(MessageEnvelope.Story storyMessage) {
        return new JsonStoryMessage(storyMessage.allowsReplies(),
                storyMessage.groupId().map(GroupId::toBase64).orElse(null),
                storyMessage.fileAttachment().map(JsonAttachment::from).orElse(null),
                storyMessage.textAttachment().map(TextAttachment::from).orElse(null));
    }

    public record TextAttachment(
            String text,
            @JsonInclude(JsonInclude.Include.NON_NULL) String style,
            @JsonInclude(JsonInclude.Include.NON_NULL) String textForegroundColor,
            @JsonInclude(JsonInclude.Include.NON_NULL) String textBackgroundColor,
            @JsonInclude(JsonInclude.Include.NON_NULL) JsonPreview preview,
            @JsonInclude(JsonInclude.Include.NON_NULL) Gradient backgroundGradient,
            @JsonInclude(JsonInclude.Include.NON_NULL) String backgroundColor
    ) {

        static TextAttachment from(MessageEnvelope.Story.TextAttachment textAttachment) {
            return new TextAttachment(textAttachment.text().orElse(null),
                    textAttachment.style().map(MessageEnvelope.Story.TextAttachment.Style::name).orElse(null),
                    textAttachment.textForegroundColor().map(Color::toHexColor).orElse(null),
                    textAttachment.textBackgroundColor().map(Color::toHexColor).orElse(null),
                    textAttachment.preview().map(JsonPreview::from).orElse(null),
                    textAttachment.backgroundGradient().map(Gradient::from).orElse(null),
                    textAttachment.backgroundColor().map(Color::toHexColor).orElse(null));
        }

        public record Gradient(
                String startColor,
                String endColor,
                List<String> colors,
                List<Float> positions,
                Integer angle
        ) {

            static Gradient from(MessageEnvelope.Story.TextAttachment.Gradient gradient) {
                final var isLegacyGradient = gradient.colors().size() == 2
                        && gradient.positions().size() == 2
                        && gradient.positions().get(0) == 0f
                        && gradient.positions().get(1) == 1f;

                return new Gradient(isLegacyGradient ? gradient.colors().get(0).toHexColor() : null,
                        isLegacyGradient ? gradient.colors().get(1).toHexColor() : null,
                        gradient.colors().stream().map(Color::toHexColor).toList(),
                        gradient.positions(),
                        gradient.angle().orElse(null));
            }
        }
    }
}
