package in.daasavaani.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.ColumnInfo;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.RoomDatabase;
import java.util.List;

@Database(entities = {PersonalDatabase.Preference.class, PersonalDatabase.Favorite.class,
        PersonalDatabase.SavedList.class, PersonalDatabase.Entry.class, PersonalDatabase.Recent.class,
        PersonalDatabase.Learning.class},
        version = 9, exportSchema = true)
public abstract class PersonalDatabase extends RoomDatabase {
    public abstract Records records();

    @Entity(tableName = "preferences")
    public static class Preference {
        @PrimaryKey public int id;
        @NonNull public String language = "device";
        @NonNull @ColumnInfo(defaultValue = "'device'") public String theme = "device";
        public long revision;
        public boolean recentHistoryEnabled;
        public int lyricFontSizeSp;
        @NonNull public String readingDensity = "";
        @Nullable public String recitationListId;
        @Nullable public String recitationEntryId;
        @ColumnInfo(defaultValue = "0") public boolean backupLanguagePending;
        @NonNull @ColumnInfo(defaultValue = "'pending'") public String setupStatus = "pending";
    }

    @Entity(tableName = "favorites", primaryKeys = {"kind", "value"})
    public static class Favorite {
        @NonNull public String kind = "";
        @NonNull public String value = "";
        public int position;
    }

    @Entity(tableName = "lists")
    public static class SavedList {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String name = "";
        public int position;
    }

    @Entity(tableName = "recents", primaryKeys = {"kind", "value"})
    public static class Recent {
        @NonNull public String kind = "";
        @NonNull public String value = "";
        public int position;
    }

    @Entity(tableName = "occurrences")
    public static class Entry {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String listId = "";
        public int position;
        @NonNull public String kind = "";
        @Nullable public String videoId;
        @Nullable public String lyricId;
        @Nullable public String fallback;
        @Nullable public String text;
    }

    @Entity(tableName = "learning", primaryKeys = {"kind", "value"})
    public static class Learning {
        @NonNull public String kind = "";
        @NonNull public String value = "";
        @NonNull public String label = "";
        public int position;
    }

    @Dao
    public interface Records {
        @Query("SELECT * FROM preferences") List<Preference> preferences();
        @Query("SELECT * FROM favorites ORDER BY position") List<Favorite> favorites();
        @Query("SELECT * FROM lists ORDER BY position") List<SavedList> lists();
        @Query("SELECT * FROM occurrences ORDER BY position") List<Entry> entries();
        @Query("SELECT * FROM recents ORDER BY position") List<Recent> recents();
        @Query("SELECT * FROM learning ORDER BY position") List<Learning> learning();
        @Insert void insertLearning(Learning assignment);
        @Query("DELETE FROM learning") void clearLearning();
        @Query("UPDATE preferences SET revision = :revision WHERE id = 1")
        void updateRevision(long revision);
        @Query("UPDATE preferences SET backupLanguagePending = CASE WHEN language = :language THEN backupLanguagePending ELSE 0 END, language = :language, theme = :theme, revision = :revision, recentHistoryEnabled = :enabled WHERE id = 1")
        void updatePreference(String language, String theme, long revision, boolean enabled);
        @Query("UPDATE preferences SET backupLanguagePending = :pending WHERE id = 1")
        void updateBackupLanguagePending(boolean pending);
        @Query("UPDATE preferences SET setupStatus = :status, revision = :revision WHERE id = 1")
        void updateSetupStatus(String status, long revision);
        @Query("UPDATE preferences SET lyricFontSizeSp = :fontSize, readingDensity = :density, revision = :revision WHERE id = 1")
        void updateReading(int fontSize, String density, long revision);
        @Query("UPDATE preferences SET recitationListId = :listId, recitationEntryId = :entryId, revision = :revision WHERE id = 1")
        void updateRecitation(String listId, String entryId, long revision);
        @Insert void insertRecent(Recent recent);
        @Query("DELETE FROM recents") void clearRecents();
        @Insert void insertFavorite(Favorite favorite);
        @Insert void insertList(SavedList list);
        @Insert void insertEntry(Entry entry);
        @Query("DELETE FROM favorites") void clearFavorites();
        @Query("DELETE FROM lists") void clearLists();
        @Query("DELETE FROM occurrences") void clearEntries();
    }
}
