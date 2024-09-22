package de.zenonet.stundenplan.common;

public enum ResultType {
    Success,
    TokenExpired,
    Offline,
    CacheMiss,
    NoLoginSaved,
    CantLoadLookupData,
    UnknownError,
    CantLoadTimeTable,
}
