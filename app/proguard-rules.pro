# Gson-serialized data classes, reflected over by field name during
# (de)serialization in ImportExportService.
-keep class me.jhot.meld.data.model.** { *; }
-keep class me.jhot.meld.service.*Export* { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Tasker plugin classes: fields/constructors are read reflectively by the
# Tasker app itself via @TaskerInputField/@TaskerOutputVariable annotations.
-keep class me.jhot.meld.tasker.** { *; }
