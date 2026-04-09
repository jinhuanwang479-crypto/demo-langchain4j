package com.example.consultant.utils;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BracketValueParser {

    private static final Pattern BRACKET_ID_PATTERN = Pattern.compile("\\[(\\d+)]");

    private BracketValueParser() {
    }

    public static Set<Long> parseIds(String bracketValue) {
        Set<Long> ids = new LinkedHashSet<>();
        if (!StringUtils.hasText(bracketValue)) {
            return ids;
        }
        Matcher matcher = BRACKET_ID_PATTERN.matcher(bracketValue);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }

    public static Set<Long> parseIds(Collection<String> bracketValues) {
        Set<Long> ids = new LinkedHashSet<>();
        if (bracketValues == null) {
            return ids;
        }
        for (String bracketValue : bracketValues) {
            ids.addAll(parseIds(bracketValue));
        }
        return ids;
    }
}
