# Unique ID used to identify this message, should preferably be consecutively increasing.
uint64 sequence_id

# Controller timestamp to sync playback in the log, nanoseconds.
int64 controllerTimestamp

# Frame of reference
string frame_id

# Origin of the grid's corner relative to frame of reference; grid is positioned in the x-y
# plane relative to this origin
geometry_msgs/Pose pose

# Number of grid columns
uint32 column_count

# Size of a single grid cell along the x and y axes, relative to `pose`
Vector2 cell_size

# Number of bytes between rows in `data`
uint32 row_stride

# Number of bytes between cells within a row in `data`
uint32 cell_stride

# Fields in `data`
PackedElementField[] fields

# Grid cell data, interpreted using `fields`, in row-major (y-major) order
uint8[] data
