package com.autogradingsystem.plagiarism.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeNormalizer
 *
 * PURPOSE:
 *   Converts raw Java source code into a canonical token sequence before
 *   fingerprinting.  Normalisation removes superficial differences (variable
 *   names, comments, whitespace, string/number literals) so that two
 *   submissions that differ only in cosmetic ways still produce highly similar
 *   fingerprints.
 *
 * NORMALISATION PIPELINE (applied in order):
 *   1. Strip block comments  /* ... *\/  (including the student header)
 *   2. Strip line comments   // ...
 *   3. Replace string literals  "..."  →  STR
 *   4. Replace char literals    '.'    →  CHR
 *   5. Replace numeric literals        →  NUM
 *   6. Tokenise on word boundaries + punctuation
 *   7. Replace identifiers that are NOT Java keywords → VAR
 *   8. Drop tokens shorter than minTokenLength
 *
 * The resulting token list is used by FingerprintService for Winnowing.
 */
public class CodeNormalizer {

    // ── Java keyword set (used to preserve structural tokens) ─────────────────

    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "abstract","assert","boolean","break","byte","case","catch","char","class",
        "const","continue","default","do","double","else","enum","extends","final",
        "finally","float","for","goto","if","implements","import","instanceof","int",
        "interface","long","native","new","package","private","protected","public",
        "return","short","static","strictfp","super","switch","synchronized","this",
        "throw","throws","transient","try","var","void","volatile","while",
        // common operators and punctuation kept as structural tokens
        "{","}","(",")",";","[","]","<",">","=","==","!=","&&","||","++","--",
        "+=","-=","*=","/=","->","::","?",":","!"
    ));

    private static final Pattern BLOCK_COMMENT  = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT   = Pattern.compile("//[^\\n]*");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern CHAR_LITERAL   = Pattern.compile("'([^'\\\\]|\\\\.)'");
    private static final Pattern NUM_LITERAL    = Pattern.compile("\\b\\d+(\\.\\d+)?[dDfFlL]?\\b");
    // Word tokens + single-char punctuation tokens
    private static final Pattern TOKEN_PATTERN  = Pattern.compile("[\\w]+|[{}()\\[\\];,.<>=!&|+\\-*/?:]");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the normalised token sequence for the given Java source.
     *
     * @param source         Raw Java source code
     * @param minTokenLength Tokens shorter than this are discarded
     * @return List of normalised tokens
     */
    public List<String> normalize(String source, int minTokenLength) {
        if (source == null || source.isEmpty()) return new ArrayList<>();

        String code = source;

        // Step 1-2: Strip comments
        code = BLOCK_COMMENT.matcher(code).replaceAll(" ");
        code = LINE_COMMENT .matcher(code).replaceAll(" ");

        // Step 3-5: Replace literals
        code = STRING_LITERAL.matcher(code).replaceAll(" STR ");
        code = CHAR_LITERAL  .matcher(code).replaceAll(" CHR ");
        code = NUM_LITERAL   .matcher(code).replaceAll(" NUM ");

        // Step 6-8: Tokenise and normalise identifiers
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(code);
        while (m.find()) {
            String token = m.group();
            if (token.length() < minTokenLength) continue;

            // Preserve keywords and synthetic literals verbatim; replace identifiers
            if (KEYWORDS.contains(token) || token.equals("STR") || token.equals("CHR") || token.equals("NUM")) {
                tokens.add(token);
            } else if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                // Identifier → normalise to VAR
                tokens.add("VAR");
            } else {
                // Punctuation / operator
                tokens.add(token);
            }
        }

        return tokens;
    }

    /**
     * Convenience overload using the default minimum token length of 3.
     */
    public List<String> normalize(String source) {
        return normalize(source, 3);
    }
}
