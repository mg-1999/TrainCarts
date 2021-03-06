package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;

public class CartAttachmentPlatformOriginal extends CartAttachment {
    private VirtualEntity actual;
    private VirtualEntity entity;

    @Override
    public void onDetached() {
        super.onDetached();
        this.entity = null;
        this.actual = null;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.actual = new VirtualEntity(this.getManager());
        this.actual.setEntityType(EntityType.SHULKER);
        this.actual.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        this.entity = new VirtualEntity(this.getManager());
        this.entity.setEntityType(EntityType.CHICKEN);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.setRelativeOffset(0.0, -0.32, 0.0);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        if (this.entity.isMountable()) {
            return this.entity.getEntityId();
        } else {
            return -1;
        }
    }

    @Override
    public void applyDefaultSeatTransform(Matrix4x4 transform) {
        transform.translate(0.0, 1.0, 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        actual.spawn(viewer, new Vector());
        entity.spawn(viewer, new Vector());
        this.getManager().getPassengerController(viewer).mount(entity.getEntityId(), actual.getEntityId());
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        this.getManager().getPassengerController(viewer).unmount(entity.getEntityId(), actual.getEntityId());
        actual.destroy(viewer);
        entity.destroy(viewer);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
        this.actual.updatePosition(transform);
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
        this.actual.syncPosition(absolute);
    }

    @Override
    public void onTick() {
    }

}