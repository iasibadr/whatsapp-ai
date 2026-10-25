package live.pharmaradio.whatsappai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class MessageHistory {
    static final long RETENTION_MS = 24 * 60 * 60 * 1000L;
    static final int MAX_MESSAGES = 200;

    static final class Entry {
        final String id;
        final String contactKey;
        final String name;
        final String number;
        final String message;
        final String direction;
        final long created;
        final StructuredResult result;

        Entry(String id, String contactKey, String name, String number, String message, String direction, long created) {
            this(id, contactKey, name, number, message, direction, created, null);
        }

        Entry(String id, String contactKey, String name, String number, String message, String direction, long created, StructuredResult result) {
            if (id == null || id.isEmpty() || id.length() > 300 || contactKey == null || contactKey.isEmpty()
                    || contactKey.length() > 200 || name == null || name.length() > 150 || number == null
                    || number.length() > 60 || message == null || message.trim().isEmpty() || message.length() > 1500
                    || !("incoming".equals(direction) || "outgoing".equals(direction)) || created < 0) {
                throw new IllegalArgumentException("Message invalide");
            }
            this.id = id;
            this.contactKey = contactKey;
            this.name = name;
            this.number = number;
            this.message = message.trim();
            this.direction = direction;
            this.created = created;
            if (result != null && !result.render().equals(this.message)) throw new IllegalArgumentException("Bilan incohérent.");
            this.result = result;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    MessageHistory(List<Entry> saved, long now) {
        for (Entry entry : saved) {
            if (now - entry.created < RETENTION_MS && entry.created <= now) {
                if (entries.stream().anyMatch(previous -> previous.id.equals(entry.id))) continue;
                if (entries.size() >= MAX_MESSAGES) throw new IllegalStateException("Boîte pleine");
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.created).reversed());
    }

    void add(Entry entry, long now) {
        expire(now);
        if (entry.created > now || now - entry.created >= RETENTION_MS) throw new IllegalArgumentException("Message expiré");
        for (Entry previous : entries) {
            if (previous.id.equals(entry.id)) {
                if (!previous.contactKey.equals(entry.contactKey) || !previous.message.equals(entry.message)
                        || !previous.direction.equals(entry.direction) || !sameResult(previous.result, entry.result)) throw new IllegalArgumentException("Message rejoué avec un autre contenu");
                return;
            }
        }
        if (entries.size() >= MAX_MESSAGES) throw new IllegalStateException("Boîte pleine");
        entries.add(entry);
        entries.sort(Comparator.comparingLong((Entry value) -> value.created).reversed());
    }

    List<Entry> all(long now) {
        expire(now);
        return new ArrayList<>(entries);
    }

    void remove(String id) {
        entries.removeIf(entry -> Objects.equals(entry.id, id));
    }

    private void expire(long now) {
        entries.removeIf(entry -> now - entry.created >= RETENTION_MS || entry.created > now);
    }

    private boolean sameResult(StructuredResult first, StructuredResult second) {
        if (first == null || second == null) return first == second;
        return first.obtained.equals(second.obtained) && first.unresolved.equals(second.unresolved) && first.nextAction.equals(second.nextAction);
    }
}
