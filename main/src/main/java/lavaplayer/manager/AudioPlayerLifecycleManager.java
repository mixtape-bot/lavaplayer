package lavaplayer.manager;

import lavaplayer.manager.event.AudioEvent;
import lavaplayer.manager.event.AudioEventListener;
import lavaplayer.manager.event.TrackEndEvent;
import lavaplayer.manager.event.TrackStartEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Triggers cleanup checks on all active audio players at a fixed interval.
 */
public class AudioPlayerLifecycleManager implements Runnable, AudioEventListener {
    private static final long CHECK_INTERVAL = 10000;

    private final ConcurrentMap<AudioPlayer, AudioPlayer> activePlayers;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong cleanupThreshold;
    private final AtomicReference<ScheduledFuture<?>> scheduledTask;

    /**
     * @param scheduler        Scheduler to use for the cleanup check task
     * @param cleanupThreshold Threshold for player cleanup
     */
    public AudioPlayerLifecycleManager(ScheduledExecutorService scheduler, AtomicLong cleanupThreshold) {
        this.activePlayers = new ConcurrentHashMap<>();
        this.scheduler = scheduler;
        this.cleanupThreshold = cleanupThreshold;
        this.scheduledTask = new AtomicReference<>();
    }

    /**
     * Initialise the scheduled task.
     */
    public void initialise() {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(this, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
        if (!scheduledTask.compareAndSet(null, task)) {
            task.cancel(false);
        }
    }

    /**
     * Stop the scheduled task.
     */
    public void shutdown() {
        ScheduledFuture<?> task = scheduledTask.getAndSet(null);
        if (task != null) {
            task.cancel(false);
        }
    }

    @Override
    public void onEvent(@NotNull AudioEvent event) {
        if (event instanceof TrackStartEvent startEvent) {
            activePlayers.put(startEvent.getPlayer(), startEvent.getPlayer());
        } else if (event instanceof TrackEndEvent endEvent) {
            activePlayers.remove(endEvent.getPlayer());
        }
    }

    @Override
    public void run() {
        for (AudioPlayer player : activePlayers.keySet()) {
            player.checkCleanup(cleanupThreshold.get());
        }
    }
}
