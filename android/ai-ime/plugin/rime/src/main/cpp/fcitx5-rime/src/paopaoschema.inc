// SPDX-License-Identifier: LGPL-2.1-or-later
// Called on the Fcitx dispatcher, never the Android UI thread. Optional
// dictionaries are prepared explicitly; default users do not compile them.
#include <fstream>
#include "paopaoschemadeploy.h"

namespace fcitx::rime {
namespace {
const std::unordered_set<std::string> paopaoSchemas{
    "paopao_frost_t9_enhanced", "paopao_ice_t9_enhanced", "paopao_default_t9_enhanced",
    "paopao_japanese", "paopao_frost_pinyin14", "paopao_ice_pinyin14",
    "t9", "paopao_frost_pinyin", "paopao_frost_t9",
    "paopao_frost_double_pinyin", "paopao_frost_double_pinyin_flypy",
    "paopao_frost_double_pinyin_abc", "paopao_frost_double_pinyin_mspy",
    "paopao_frost_double_pinyin_sogou", "paopao_frost_double_pinyin_ziguang",
    "paopao_ice_pinyin", "paopao_ice_t9", "paopao_ice_double_pinyin",
    "paopao_ice_double_pinyin_flypy", "paopao_ice_double_pinyin_abc",
    "paopao_ice_double_pinyin_mspy", "paopao_ice_double_pinyin_sogou",
    "paopao_ice_double_pinyin_ziguang"};

bool schemaVersionMatches(rime_api_t *api, const std::string &schema,
                          const std::string &version) {
    RimeConfig config{};
    if (!api->schema_open(schema.c_str(), &config)) return false;
    char actual[256]{};
    const bool matches = version.empty() ||
        (api->config_get_string(&config, "paopao_resource_version", actual,
                                sizeof(actual)) && version == actual);
    api->config_close(&config);
    return matches;
}
} // namespace

void RimeEngine::syncPrediction(RimeSessionId session, InputContext &ic) {
    if (!session || api_->is_maintenance_mode()) return;
    char schema[256]{};
    const bool supported = api_->get_current_schema(session, schema, sizeof(schema)) &&
        (std::string_view(schema).starts_with("paopao_frost_") ||
         std::string_view(schema).starts_with("paopao_ice_"));
    const bool enabled = predictionEnabled_ && supported &&
        !ic.capabilityFlags().test(CapabilityFlag::Password) &&
        !api_->get_option(session, RIME_ASCII_MODE);
    const bool previous = api_->get_option(session, "prediction");
    if (previous == enabled) return;
    api_->set_option(session, "prediction", enabled);
    // Disabling clears only a zero-input prediction menu, never the user's
    // in-progress pinyin. Clear while disabled to prevent the plugin refilling it.
    const char *input = api_->get_input(session);
    if (!enabled && (!input || !*input)) api_->clear_composition(session);
}

void RimeEngine::paopaoPrediction(const RawConfig &request) {
    predictionResponse_.errorCode.setValue("");
    try {
        const auto enabled = paopao::vocabulary::field(request, "Enabled");
        if (enabled != "True" && enabled != "False") throw std::runtime_error("INVALID_VALUE");
        if (!api_->find_module("predict")) throw std::runtime_error("PREDICT_MODULE_MISSING");
        if (api_->is_maintenance_mode()) throw std::runtime_error("RIME_BUSY");
        predictionEnabled_ = enabled == "True";
        instance_->inputContextManager().foreach([&](InputContext *ic) {
            auto *existing = state(ic);
            const auto session = existing ? existing->session(false) : 0;
            if (session) {
                syncPrediction(session, *ic);
                if (instance_->inputMethod(ic) == "rime") existing->updateUI(ic);
            }
            return true;
        });
        predictionResponse_.enabled.setValue(predictionEnabled_);
    } catch (const std::exception &error) {
        predictionResponse_.errorCode.setValue(error.what());
    }
}

void RimeEngine::updatePaopaoSchemas() {
    const auto build = StandardPaths::global().userDirectory(StandardPathsType::PkgData) / "rime" / "build";
    for (const auto &schema : paopaoSchemas) {
        if (schema != "t9" && std::filesystem::exists(build / (schema + ".schema.yaml"))) {
            // RimeState::restore uses this set. Keep prepared profiles
            // restorable without adding them to the keyboard's schema menu.
            schemas_.insert(schema);
            optionActions_.erase(schema);
            updateActionsForSchema(schema);
        }
    }
}

void RimeEngine::paopaoSchema(const RawConfig &request) {
    schemaResponse_.schema.setValue("");
    schemaResponse_.preparedSchema.setValue("");
    schemaResponse_.errorCode.setValue("");
    schemaResponse_.error.setValue("");
    schemaResponse_.requestId.setValue(paopao::vocabulary::field(request, "RequestId"));
    const auto schema = paopao::vocabulary::field(request, "Schema");
    const auto action = paopao::vocabulary::field(request, "Action");
    try {
        if (!paopaoSchemas.count(schema)) throw std::runtime_error("UNSUPPORTED_SCHEMA");
        if (!action.empty() && action != "select" && action != "prepare")
            throw std::runtime_error("UNSUPPORTED_ACTION");

        // Startup maintenance uses a worker. Consume its completion before
        // opening a session, including the queued app-option refresh that
        // would otherwise release our newly selected session afterwards.
        api_->join_maintenance_thread();
        if (api_->is_maintenance_mode()) throw std::runtime_error("RIME_BUSY");
        if (needRefreshAppOption_) {
            api_->deploy_config_file("fcitx5.yaml", "config_version");
            updateAppOptions();
            needRefreshAppOption_ = false;
            updateSchemaMenu();
        }

        const auto shared = std::filesystem::path(sharedDataDir_);
        const auto user = StandardPaths::global().userDirectory(StandardPathsType::PkgData) / "rime";
        std::string version;
        const bool enhanced = schema.size() > 9 && schema.compare(schema.size() - 9, 9, "_enhanced") == 0;
        if (enhanced && !std::filesystem::exists(user / "wanxiang-paopao-769854c8.gram"))
            throw std::runtime_error("LANGUAGE_MODEL_MISSING");
        const bool optional = schema != "t9" && schema != "paopao_default_t9_enhanced";
        const std::string dictionary = schema == "paopao_japanese" ? "jaroomaji" : optional
            ? (schema.rfind("paopao_ice_", 0) == 0 ? "paopao_ice" : "paopao_frost")
            : "rime_ice";
        if (optional) {
            std::ifstream input(shared / (dictionary + ".version"));
            std::getline(input, version);
            if (version.empty() || version.size() > 128)
                throw std::runtime_error("RESOURCE_VERSION_MISSING");
        }
        const auto build = user / "build";
        const bool compiled = schemaVersionMatches(api_, schema, version) &&
            std::filesystem::exists(build / (std::string(dictionary) + ".table.bin")) &&
            paopao::rime::compiledSchemaPresent(api_, build, schema) &&
            paopao::rime::dependenciesPresent(api_, build, schema);
        if (!compiled && std::filesystem::exists(shared / "paopao-precompiled"))
            throw std::runtime_error("COMPILED_RESOURCE_MISSING");
        if (!compiled) {
            bool composing = false;
            instance_->inputContextManager().foreach([&](InputContext *context) {
                auto *existing = state(context);
                const auto session = existing ? existing->session(false) : 0;
                const auto *input = session ? api_->get_input(session) : nullptr;
                if (input && *input) composing = true;
                return true;
            });
            if (composing) throw std::runtime_error("INPUT_ACTIVE");
            // No live translator may hold the table while the deployer
            // rebuilds it. This does not delete or rewrite any userdb.
            releaseAllSession(true);
            if (!paopao::rime::deploySchema(api_, shared, schema) ||
                !schemaVersionMatches(api_, schema, version))
                throw std::runtime_error("DEPLOY_FAILED");
            if (!std::filesystem::exists(build / (std::string(dictionary) + ".table.bin")) ||
                !paopao::rime::compiledSchemaPresent(api_, build, schema) ||
                !paopao::rime::dependenciesPresent(api_, build, schema))
                throw std::runtime_error("COMPILED_RESOURCE_MISSING");
            optionActions_.erase(schema);
            updateActionsForSchema(schema);
        }
        schemas_.insert(schema);
        schemaResponse_.preparedSchema.setValue(schema);
        if (action == "prepare") {
            const auto probe = api_->create_session();
            if (!probe) throw std::runtime_error("SESSION_UNAVAILABLE");
            bool ready = api_->select_schema(probe, schema.c_str());
            if (ready) {
                api_->set_option(probe, "incognito", true);
                api_->process_key(probe, schema == "t9" || schema.find("_t9") != std::string::npos ? '6' : 'n', 0);
                RIME_STRUCT(RimeContext, context);
                ready = api_->get_context(probe, &context);
                if (ready) { ready = context.menu.num_candidates > 0; api_->free_context(&context); }
            }
            api_->destroy_session(probe);
            if (!ready) throw std::runtime_error("SCHEMA_LOAD_FAILED");
            return;
        }

        auto *ic = instance_->inputContextManager().lastFocusedInputContext();
        if (!ic) throw std::runtime_error("NO_INPUT_CONTEXT");
        if (instance_->inputMethod(ic) != "rime")
            throw std::runtime_error("INPUT_METHOD_NOT_RIME");
        auto *rimeState = state(ic);
        const auto session = rimeState ? rimeState->session() : 0;
        if (!session) throw std::runtime_error("SESSION_UNAVAILABLE");
        char actual[256]{};
        const bool alreadySelected = api_->get_current_schema(session, actual, sizeof(actual)) && schema == actual;
        if (!alreadySelected) rimeState->selectSchema(schema);
        if (!api_->get_current_schema(session, actual, sizeof(actual)) || schema != actual)
            throw std::runtime_error("SCHEMA_SELECTION_FAILED");
        schemaResponse_.schema.setValue(actual);
        if (!alreadySelected) refreshStatusArea(*ic);
    } catch (const std::exception &error) {
        schemaResponse_.errorCode.setValue(error.what());
        schemaResponse_.error.setValue("Unable to prepare or select the requested input dictionary.");
    }
}
} // namespace fcitx::rime
