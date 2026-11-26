# Règles R8 de l'app. Les bibliothèques (Room, WorkManager, Glance, DataStore, coroutines) fournissent
# leurs propres règles ; on ne garde ici que ce que l'app instancie par réflexion.

# Glance : actionRunCallback<T>() enregistre le NOM de la classe, instanciée par réflexion au clic.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager : workers instanciés par réflexion à partir de leur nom.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Room : implémentation générée de la base, chargée par réflexion (GoalDatabase_Impl).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Traces de plantage lisibles (numéros de ligne) sans garder les noms de fichiers source.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile
