package io.github.jackotom.hongguo.android;

/** Resolves confirmed surface taps without sending the first half of a double tap to the controls. */
final class PlayerSurfaceTaps {
    private final PlayerHud.Scheduler scheduler;
    private final Runnable single, twice, confirm;
    private final long timeout;
    private final int slop;
    private boolean pending;
    private float x, y;

    PlayerSurfaceTaps(PlayerHud.Scheduler scheduler, Runnable single, Runnable twice, long timeout, int slop) {
        this.scheduler = scheduler;
        this.single = single;
        this.twice = twice;
        this.timeout = timeout;
        this.slop = slop;
        confirm = () -> {
            if (!pending) return;
            pending = false;
            single.run();
        };
    }

    void tap(float atX, float atY) {
        if (pending) {
            boolean nearby = Math.hypot(atX - x, atY - y) <= slop;
            cancel();
            if (nearby) { twice.run(); return; }
            single.run();
        }
        x = atX;
        y = atY;
        pending = true;
        scheduler.later(confirm, timeout);
    }

    void cancel() {
        pending = false;
        scheduler.cancel(confirm);
    }
}
