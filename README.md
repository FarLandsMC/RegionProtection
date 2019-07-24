# RegionProtection
Please read the following information if you plan to contribute to this plugin or make custom, local edits to the plugin
source.

## Building the Plugin
The only dependency of this plugin is spigot, which should be locally built using the BuildTools jar which can be found
[here](https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target). Probably the simplest way to
build the plugin is to run the build script (`build.sh`) from the project root directory. This script will automatically
create the spigot jar if it cannot be found, and will create the plugin jar in a directory named `out`. If you wish to build
the plugin from your IDE, add the spigot jar to the classpath, and use the source compile output along with any resource
files to create the jar (no manifest is used).

## Basic Plugin Layout
Packages with descriptions:
- `command`: command executor and tab completer implementations.
- `data`: classes related to transient and persistent data, as well as serialization and deserialization.
- `data.flagdata`: dedicated to flag metadata classes to prevent clutter.
- `event`: event handlers.
- `util`: non-related utility classes.

The RegionProtection class contains a data manager instance, where all non-config plugin data can be accessed. Config data
can be retrieved directly through the RegionProtection class. On enabling of the plugin, for each world, a region lookup
table will be generated. Also, all persistent player data will be loaded. When an event is handled, generally speaking the
flags present at the location of the event will be looked up through the data manager, and the event will be handled based on
the metadata of the flags retrieved.

## Commands
Command implementations are stored as separate classes in the command package, and are registered in the RegionProtection
class. The command implementation classes act as both the executor and tab completer (if the command has tab completion).

## Data & Management
As mentioned before, data is managed by the DataManager, which can be accessed statically through the RegionProtection class.
Persistent data is stored in two files: `regions.dat` and `playerdata.dat`. The format of these files is non-standard, and is
defined by the Serializer class. Data is loaded once upon enabling of the plugin, and is saved periodically while the server
is running, and is saved once more when the plugin is disabled. Region data is stored in the Region class instances, which
are stored in region lookup tables. The data manager provides an interface to find regions at given locations. Player data
comes in two forms: persistent and transient. Persistent data is stored in the PersistentPlayerData class and transient data
is stored in the PlayerSession class. Note: persistent data usually should not be accessed directly, since if a player
session is created, its data will be stored in the persistent data class when the session is destroyed. The data manager
provides methods to handle player data, and will check for an active session before modifying the persistent data directly.

### Regions
Regions are fairly straight forward. They have an owner (UUID), a name, priority, flag set, minimum and maximum vertices, and
a list of child regions. Child regions are not stored in a world's region list, rather they are stored with their parent
region. However, the region lookup table contains child regions. If a child region has the same priority as the parent
region, then the child will mirror the flags of the parent region. If a region is deleted, then its children must be removed
as well.

### Flags
Region flags are stored in the RegionFlag enum. Flags are defined with two fields: whether or not the flag is player-
toggleable, and the metadata class of the flag. If a flag is declared to be player-toggleable, then the flag value can be
changed with the `claimtoggle` command (currently this command's implementation assumes that all player toggleable flags have
boolean metadata). The metadata class specifies what type of data is associated with the flag, either boolean (allow/deny,
true/false), or something more complicated such as an enum filter or command metadata. Flags should **not** be deleted, and
new flags should **only** be added to the end of the enum since the flag ordinals are stored. The only pieces of information
that can be changed for pre-existing flags are the flag's name and whether or not the flag is player-toggleable.

### Flag Handling
Flag metadata classes are handled in many different places: tab completer of the region command, serializer class,
deserializer class, and RegionFlag class (`toString` and `metaFromString` classes). If a new flag is created and it's not a
boolean flag, often the flag will need to be explicitly handled in these various locations for it to function properly. The
actual implementation of the flags is in the event handler classes.

### Flag Values
Flag values are stored in instances of the FlagContainer class. If a flag container has an explicitly defined value for a
flag, then that value will be returned when the flag is queried. Otherwise, the default value of the flag in that context
will be returned. Flags have two default values: region default values, and world default values. Region defaults are
configureable, while world default values match the vanilla settings in a given world (this is used to prevent unintended
behavior with global flags).

### Resolving Flag Conflicts
There are two types of flag containers: regions and global containers. Regions have a priority as mentioned before, and the
larger the numeric priority, the higher the effective priority. If there are multiple regions at a single location with
conflicting flag values, the highest priority region's flag value will be used. Global flag values will always have a lower
priority than any region.

## Event Handlers
The event handlers are divided into four categories: region-tool, player, entity, and world.
- `region-tool`: events related with region interaction, such as region creation and inquiry.
- `player`: events caused by or directly related to a player.
- `entity`: events caused by or directly related to entities other than players.
- `world`: events related to the server tick not caused by entities.

When implementing a flag, it is very important to note that if no flags are present at a location, the data manager will
return null rather than an empty flag container.