package cn.lineai.data.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * BM25 排序与相关性评分算法。纯算法实现，不依赖数据库。
 */
public final class MemoryRanker {
    static final double RECENCY_WEIGHT = 0.15;
    private static final double BM25_K = 1.2;
    private static final double BM25_B = 0.75;

    private MemoryRanker() {
    }

    public static List<Candidate> rank(List<Candidate> candidates, String userInput, int limit, boolean allowRecentFallback) {
        return rank(candidates, userInput, limit, allowRecentFallback, 0.0);
    }

    public static List<Candidate> rank(List<Candidate> candidates, String userInput, int limit, boolean allowRecentFallback, double boost) {
        long now = System.currentTimeMillis();
        boolean hasMatches = false;
        for (Candidate candidate : candidates) {
            candidate.relevanceScore = relevanceScore(userInput, candidate.searchText);
            candidate.score = rankingScore(userInput, candidate.searchText, candidate.updatedAt, now, boost);
            hasMatches = hasMatches || candidate.relevanceScore > 0;
        }
        if (!hasMatches && !allowRecentFallback) {
            return Collections.emptyList();
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                int scoreCompare = Double.compare(right.score, left.score);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return Long.compare(right.updatedAt, left.updatedAt);
            }
        });
        ArrayList<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (hasMatches && candidate.relevanceScore <= 0) {
                continue;
            }
            if (!hasMatches && candidate.score <= 0) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= limit) {
                break;
            }
        }
        return selected;
    }

    static double rankingScore(String query, String text, long updatedAt, long now, double boost) {
        return relevanceScore(query, text) + recencyBoost(updatedAt, now) * RECENCY_WEIGHT + Math.max(0.0, boost);
    }

    static double relevanceScore(String query, String text) {
        List<String> queryKeywords = TextTokenizer.extractKeywords(query);
        List<String> docKeywords = TextTokenizer.extractKeywords(text);
        if (queryKeywords.isEmpty() || docKeywords.isEmpty()) {
            return 0.0;
        }
        HashMap<String, Integer> queryTerms = TextTokenizer.termFrequency(queryKeywords);
        HashMap<String, Integer> docTerms = TextTokenizer.termFrequency(docKeywords);
        int docLength = Math.max(1, docKeywords.size());
        String rawText = TextTokenizer.safeStatic(text).toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (String term : queryTerms.keySet()) {
            int docTf = docTerms.containsKey(term) ? docTerms.get(term) : rawText.contains(term) ? 1 : 0;
            if (docTf <= 0) {
                continue;
            }
            int queryTf = queryTerms.get(term);
            double tf = docTf / (docTf + BM25_K + BM25_B * docLength / 100.0);
            score += (1.0 + Math.log(queryTf)) * tf;
        }
        String queryPhrase = TextTokenizer.safeStatic(query).trim().toLowerCase(Locale.ROOT);
        if (queryPhrase.length() >= 4 && rawText.contains(queryPhrase)) {
            score += 2.0;
        }
        return score;
    }

    static double recencyBoost(long updatedAt, long now) {
        if (updatedAt <= 0 || now <= 0) {
            return 0.0;
        }
        double ageDays = Math.max(0.0, (now - updatedAt) / 86400000.0);
        return 1.0 / (1.0 + ageDays / 30.0);
    }

    public static final class Candidate {
        public final String id;
        public final String searchText;
        public final long updatedAt;
        public final String formatted;
        public double relevanceScore;
        public double score;

        public Candidate(String id, String searchText, long updatedAt, String formatted) {
            this.id = id == null ? "" : id;
            this.searchText = searchText == null ? "" : searchText;
            this.updatedAt = updatedAt;
            this.formatted = formatted == null ? "" : formatted;
        }
    }
}
