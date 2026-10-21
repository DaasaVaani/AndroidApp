package in.daasavaani.app;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.ColumnInfo;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.RoomDatabase;
import java.util.List;

@Database(entities = {PersonalDatabase.Preference.class, PersonalDatabase.Favorite.class},
        version = 2, exportSchema = true)
public abstract class PersonalDatabase extends RoomDatabase {
    public abstract Records records();

    @Entity(tableName = "preferences")
    public static class Preference {
        @PrimaryKey public int id;
        @NonNull public String language = "device";
        @NonNull @ColumnInfo(defaultValue = "'device'") public String theme = "device";
        public long revision;
    }

    @Entity(tableName = "favorites")
    public static class Favorite {
        @PrimaryKey @NonNull public String videoId = "";
    }

    @Dao
    public interface Records {
        @Query("SELECT * FROM preferences") List<Preference> preferences();
        @Query("SELECT * FROM favorites ORDER BY videoId") List<Favorite> favorites();
        @Query("UPDATE preferences SET language = :language, theme = :theme, revision = :revision WHERE id = 1")
        void updatePreference(String language, String theme, long revision);
        @Insert void insertFavorite(Favorite favorite);
        @Query("DELETE FROM favorites WHERE videoId = :videoId") void deleteFavorite(String videoId);
    }
}
