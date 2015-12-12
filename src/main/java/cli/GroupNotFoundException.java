package cli;

public class GroupNotFoundException extends Exception {
    private final byte[] groupId;

    public GroupNotFoundException(byte[] groupId) {
        super();
        this.groupId = groupId;
    }

    public byte[] getGroupId() {
        return groupId;
    }
}
