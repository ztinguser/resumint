package com.resumint.system;

import java.util.regex.Pattern;

public final class LogSanitizer {
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[ -]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern SECRET = Pattern.compile("(?i)(authorization|api[-_ ]?key|token|secret|code)\\s*[:=]\\s*[^\\s,;]+",Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_URL = Pattern.compile("data:image/[^;]+;base64,[A-Za-z0-9+/=_-]+",Pattern.CASE_INSENSITIVE);
    private LogSanitizer() { }
    public static String sanitize(String value) {
        if(value==null)return "unknown";
        String safe=DATA_URL.matcher(value).replaceAll("[image-data-redacted]");
        safe=PHONE.matcher(safe).replaceAll("[phone-redacted]");
        safe=SECRET.matcher(safe).replaceAll("$1=[redacted]");
        return safe.length()>500?safe.substring(0,500)+"…":safe;
    }
}
