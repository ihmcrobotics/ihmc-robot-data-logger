uint8 handshakeFileType
# Handshake file name
string handshake
# Data file name
string data
# Summary file name
string summary
# Variable index file
string index
# Does the index contain timestamps
bool timestamped
# Is the data compressed
bool compressed
# Number of ticks per compressed block; 0 or 1 means per-tick (legacy)
int64 compressionBatchSize
# Number of valid ticks in the last compressed block; 0 means all ticks are valid (legacy)
int64 validTicksInLastBatch
# Compression codec used: "zstd" or empty/absent means snappy (legacy)
string compressionType
