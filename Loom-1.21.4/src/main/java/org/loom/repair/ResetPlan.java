package org.loom.repair;

import java.util.ArrayList;
import java.util.List;

/**
 * A plan containing all positions that need to be cleared during a reset.
 */
public class ResetPlan {

    private final String jobId;
    private final List<ResetEntry> entries;
    private int index;

    public ResetPlan(String jobId) {
        this.jobId = jobId;
        this.entries = new ArrayList<>();
        this.index = 0;
    }

    public String getJobId() { return jobId; }
    public void addEntry(ResetEntry entry) { entries.add(entry); }
    public ResetEntry next() {
        if (index >= entries.size()) return null;
        return entries.get(index++);
    }
    public int size() { return entries.size(); }
    public int remaining() { return entries.size() - index; }
    public boolean isComplete() { return index >= entries.size(); }

    @Override
    public String toString() {
        return "ResetPlan{" + entries.size() + " entries}";
    }
}
