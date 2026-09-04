# A field present within each element in a byte array of packed elements.
# Field layout matches Foxglove's foxglove_msgs/msg/PackedElementField so this can be used
# in the `fields` array of a Grid message that Foxglove's 3D panel will recognize natively.

# Name of the field
string name

# Byte offset from start of data buffer
uint32 offset

# Unknown numeric type
uint8 UNKNOWN = 0

# Unsigned 8-bit integer
uint8 UINT8 = 1

# Signed 8-bit integer
uint8 INT8 = 2

# Unsigned 16-bit integer
uint8 UINT16 = 3

# Signed 16-bit integer
uint8 INT16 = 4

# Unsigned 32-bit integer
uint8 UINT32 = 5

# Signed 32-bit integer
uint8 INT32 = 6

# 32-bit floating-point number
uint8 FLOAT32 = 7

# 64-bit floating-point number
uint8 FLOAT64 = 8

# Type of data in the field. Integers are stored using little-endian byte order.
uint8 type
