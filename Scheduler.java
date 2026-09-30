package com.example.elementalgaze;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.IntConsumer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 지연 실행 큐. 스킬의 연출/다단히트/지속 효과를 엔티티 없이 처리한다.
 * 대기 중인 작업이 있을 때만 비용이 들고, 전체 작업 수에 상한이 있다.
 */
@Mod.EventBusSubscriber(modid = ElementalGaze.MODID)
public final class Scheduler {
    private record Task(long at, Runnable run) {}

    private static final int MAX_TASKS = 4000;
    private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>(Comparator.comparingLong(Task::at));
    private static long now;

    private Scheduler() {}

    public static void later(int delay, Runnable r) {
        if (QUEUE.size() >= MAX_TASKS) return;
        QUEUE.add(new Task(now + Math.max(1, delay), r));
    }

    public static void repeat(int delay, int period, int times, IntConsumer step) {
        for (int i = 0; i < times; i++) {
            final int idx = i;
            later(delay + i * period, () -> step.accept(idx));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        now++;
        Task t;
        while ((t = QUEUE.peek()) != null && t.at() <= now) {
            QUEUE.poll();
            try {
                t.run().run();
            } catch (RuntimeException ex) {
                ElementalGaze.LOGGER.error("Scheduled task failed", ex);
            }
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        QUEUE.clear();
        SkillExecutor.clearTokens();
    }
}
