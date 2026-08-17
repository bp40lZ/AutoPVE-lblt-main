package dev.b_p40lz.autopve.utils;

import java.util.concurrent.ConcurrentLinkedDeque;

public class PacketTracker {
    private static final int WINDOW_MS = 1000;

    private static final ConcurrentLinkedDeque<Long> sentTimestamps = new ConcurrentLinkedDeque<>();

    public static void addSent() {
        sentTimestamps.addLast(System.currentTimeMillis());
    }

    public static int getSentCount() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!sentTimestamps.isEmpty() && sentTimestamps.peekFirst() < cutoff) {
            sentTimestamps.pollFirst();
        }
        return sentTimestamps.size();
    }
}
