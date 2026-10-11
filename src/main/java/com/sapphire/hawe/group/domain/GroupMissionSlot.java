package com.sapphire.hawe.group.domain;

import java.time.LocalTime;

public class GroupMissionSlot {

    private Long groupId;
    private int slotNo;
    private LocalTime publishTime;
    private LocalTime deadlineTime;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public int getSlotNo() {
        return slotNo;
    }

    public void setSlotNo(int slotNo) {
        this.slotNo = slotNo;
    }

    public LocalTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalTime publishTime) {
        this.publishTime = publishTime;
    }

    public LocalTime getDeadlineTime() {
        return deadlineTime;
    }

    public void setDeadlineTime(LocalTime deadlineTime) {
        this.deadlineTime = deadlineTime;
    }
}
