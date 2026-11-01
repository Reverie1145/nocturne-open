package cn.reverie.manager;

/** Bridge for the Via hook skipped when Nocturne replaces sendPosition. */
public interface LegacySneakingSender {
    void nocturne$sendLegacySneakingPacket();
}
