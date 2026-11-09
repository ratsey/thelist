# Room, Compose and AndroidX ship their own consumer rules, so this stays small.

# Keep the update API's JSON model field access predictable if reflection is ever added.
-keepclassmembers class app.trolley.lists.update.** { *; }
