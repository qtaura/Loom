package org.loom.repair;

import java.util.ArrayList;
import java.util.List;

/**
 * A plan containing all positions that need repair in the current build area.
 */
public class RepairPlan {

    private final String jobId;
    private final List<RepairEntry> entries;
    private int index;

    public RepairPlan(String jobId) {
        this.jobId = jobId;
        this.entries = new ArrayList<>();
        this.index = 0;
    }

    public String getJobId() { return jobId; }

    public void addEntry(RepairEntry entry) {
        entries.add(entry);
    }

    public RepairEntry next() {
        if (index >= entries.size()) return null;
        return entries.get(index++);
    }

    public int size() { return entries.size(); }
    public int remaining() { return entries.size() - index; }
    public boolean isComplete() { return index >= entries.size(); }
    public List<RepairEntry> getEntries() { return List.copyOf(entries); }

    @Override
    public String toString() {
        return "RepairPlan{" + entries.size() + " entries, " + remaining() + " remaining}";
    }
}
