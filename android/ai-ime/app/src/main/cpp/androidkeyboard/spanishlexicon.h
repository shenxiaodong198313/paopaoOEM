// SPDX-License-Identifier: LGPL-2.1-or-later
#pragma once
#include <algorithm>
#include <fstream>
#include <string>
#include <vector>

namespace paopao {
inline std::string spanishFold(const std::string &text) {
    std::string result;
    for (size_t i = 0; i < text.size(); ++i) {
        auto c = static_cast<unsigned char>(text[i]);
        if (c == 0xc3 && i + 1 < text.size()) {
            auto next = static_cast<unsigned char>(text[++i]);
            switch (next) {
            case 0xa1: case 0x81: result += 'a'; break;
            case 0xa9: case 0x89: result += 'e'; break;
            case 0xad: case 0x8d: result += 'i'; break;
            case 0xb3: case 0x93: result += 'o'; break;
            case 0xba: case 0x9a: case 0xbc: case 0x9c: result += 'u'; break;
            case 0xb1: case 0x91: result += "ñ"; break;
            default: result += static_cast<char>(c); result += static_cast<char>(next);
            }
        } else result += static_cast<char>(c >= 'A' && c <= 'Z' ? c + 32 : c);
    }
    return result;
}

class SpanishLexicon {
    std::vector<std::pair<std::string, std::string>> words_;
public:
    explicit SpanishLexicon(const std::string &file) {
        std::ifstream input(file);
        for (std::string word; std::getline(input, word);) {
            if (!word.empty()) words_.emplace_back(spanishFold(word), word);
        }
        std::sort(words_.begin(), words_.end());
        words_.erase(std::unique(words_.begin(), words_.end()), words_.end());
    }
    std::vector<std::pair<std::string, std::string>> hint(const std::string &input, size_t count) const {
        const auto prefix = spanishFold(input);
        if (prefix.empty()) return {};
        auto start = std::lower_bound(words_.begin(), words_.end(), std::make_pair(prefix, std::string()));
        std::vector<std::string> matches;
        for (auto it = start; it != words_.end() && it->first.compare(0, prefix.size(), prefix) == 0; ++it)
            matches.push_back(it->second);
        std::sort(matches.begin(), matches.end(), [&](const auto &a, const auto &b) {
            const bool exactA = spanishFold(a) == prefix, exactB = spanishFold(b) == prefix;
            if (exactA != exactB) return exactA;
            return a.size() == b.size() ? a < b : a.size() < b.size();
        });
        if (matches.size() > count) matches.resize(count);
        std::vector<std::pair<std::string, std::string>> result;
        for (auto word : matches) {
            if (!input.empty() && input[0] >= 'A' && input[0] <= 'Z' && word[0] >= 'a' && word[0] <= 'z') word[0] -= 32;
            result.emplace_back(word, word);
        }
        return result;
    }
};
}
