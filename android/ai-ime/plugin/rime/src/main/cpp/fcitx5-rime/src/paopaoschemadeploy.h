// SPDX-License-Identifier: LGPL-2.1-or-later
#pragma once

#include <filesystem>
#include <rime_api.h>
#include <string>
#include <vector>

namespace paopao::rime {
// RimeDeploySchema updates one schema only. WorkspaceUpdate normally handles
// dependencies, but optional profiles intentionally stay outside schema_list.
inline std::vector<std::string> schemaDependencies(RimeApi *api, const std::string &id) {
    std::vector<std::string> result;
    RimeConfig config{};
    if (!api->schema_open(id.c_str(), &config)) return result;
    RimeConfigIterator it{};
    if (api->config_begin_list(&it, &config, "schema/dependencies")) {
        while (api->config_next(&it)) {
            char dependency[256]{};
            if (api->config_get_string(&config, it.path, dependency, sizeof(dependency)))
                result.emplace_back(dependency);
        }
        api->config_end(&it);
    }
    api->config_close(&config);
    return result;
}

inline bool compiledSchemaPresent(RimeApi *api, const std::filesystem::path &build,
                                  const std::string &id) {
    if (!std::filesystem::exists(build / (id + ".schema.yaml"))) return false;
    RimeConfig config{};
    if (!api->schema_open(id.c_str(), &config)) return false;
    char dictionary[256]{}, prism[256]{};
    api->config_get_string(&config, "translator/dictionary", dictionary, sizeof(dictionary));
    api->config_get_string(&config, "translator/prism", prism, sizeof(prism));
    api->config_close(&config);
    if (!*dictionary) return true;
    return std::filesystem::exists(build / (std::string(dictionary) + ".table.bin")) &&
        std::filesystem::exists(build / (std::string(*prism ? prism : dictionary) + ".prism.bin"));
}

inline bool dependenciesPresent(RimeApi *api, const std::filesystem::path &build,
                                const std::string &id) {
    for (const auto &dependency : schemaDependencies(api, id))
        if (!compiledSchemaPresent(api, build, dependency)) return false;
    return true;
}

inline bool deploySchema(RimeApi *api, const std::filesystem::path &shared,
                         const std::string &id) {
    // Null traits retain the existing shared/user paths.
    api->deployer_initialize(nullptr);
    if (!api->deploy_schema((shared / (id + ".schema.yaml")).c_str())) return false;
    for (const auto &dependency : schemaDependencies(api, id)) {
        if (dependency != "melt_eng" && dependency != "radical_pinyin" &&
            dependency != "paopao_pinyin_english") return false;
        if (!api->deploy_schema((shared / (dependency + ".schema.yaml")).c_str())) return false;
    }
    return true;
}
} // namespace paopao::rime
