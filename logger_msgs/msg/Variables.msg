uint8 handshakeFileType
string handshake    # Handshake file name
string data    # Data file name
string summary    # Summary file name
string index    # Variable index file
bool timestamped    # Does the index contain timestamps
bool compressed    # Is the data compressed
int64 compressionBatchSize    # Number of ticks per compressed block; 0 or 1 means per-tick (legacy)
int64 validTicksInLastBatch    # Number of valid ticks in the last compressed block; 0 means all ticks are valid (legacy)
string compressionType    # Compression codec used: "zstd" or empty/absent means snappy (legacy)
