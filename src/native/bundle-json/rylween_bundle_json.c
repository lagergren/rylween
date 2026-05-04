#include "json5.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
    BUNDLE_SCHEMA_VERSION = 3,
    RUNTIME_SYSTEMS_SCHEMA_VERSION = 1
};

static const char *SECTION_ORDER[] = {
    "universe",
    "limits",
    "strings",
    "handles",
    "flags",
    "items",
    "itemPools",
    "formulas",
    "rewards",
    "terrain",
    "worldMaps",
    "images",
    "staticScreens",
    "screenRegions",
    "towns",
    "buildingServices",
    "characters",
    "monsters",
    "missions",
    "spells",
    "itemUses",
    "dungeonEvents",
    "dungeonScenarios",
    "scripts",
    "runtimeSystems",
    "runtimeFacade",
    "states",
};

static const size_t SECTION_COUNT = sizeof SECTION_ORDER / sizeof SECTION_ORDER[0];

static void fail(const char *message) {
    fprintf(stderr, "native JSON bundle reader failed: %s\n", message);
    exit(1);
}

static const node *expect_kind(const node *n, node_kind kind, const char *label) {
    if (!n || n->kind != kind) {
        fprintf(stderr, "native JSON bundle reader failed: expected %s\n", label);
        exit(1);
    }
    return n;
}

static const node *req(const node *obj, const char *key, node_kind kind) {
    char label[160];
    snprintf(label, sizeof label, "%s", key);
    const node *value = obj_get(expect_kind(obj, N_OBJ, "object"), key);
    return expect_kind(value, kind, label);
}

static const char *req_str(const node *obj, const char *key) {
    return req(obj, key, N_STR)->u.sv;
}

static int32_t req_int(const node *obj, const char *key) {
    return req(obj, key, N_INT)->u.iv;
}

static const node *req_obj(const node *obj, const char *key) {
    return req(obj, key, N_OBJ);
}

static const node *req_arr(const node *obj, const char *key) {
    return req(obj, key, N_ARR);
}

static int str_eq(const char *a, const char *b) {
    return strcmp(a ? a : "", b ? b : "") == 0;
}

static void expect_string_value(const node *n, const char *expected, const char *label) {
    expect_kind(n, N_STR, label);
    if (!str_eq(n->u.sv, expected)) {
        fprintf(stderr, "native JSON bundle reader failed: expected %s '%s', found '%s'\n",
                label, expected, n->u.sv ? n->u.sv : "");
        exit(1);
    }
}

static const node *find_obj_by_string_field(const node *arr, const char *field, const char *value) {
    expect_kind(arr, N_ARR, "array");
    for (size_t i = 0; i < arr->u.arr.n; i++) {
        const node *row = expect_kind(arr->u.arr.items[i], N_OBJ, "array object");
        const node *field_node = obj_get(row, field);
        if (field_node && field_node->kind == N_STR && str_eq(field_node->u.sv, value)) {
            return row;
        }
    }
    return NULL;
}

static void reject_reflective_type_markers(const node *n, const char *path) {
    if (!n) {
        return;
    }
    if (n->kind == N_OBJ) {
        for (size_t i = 0; i < n->u.obj.n; i++) {
            if (str_eq(n->u.obj.items[i].key, "_type")) {
                fprintf(stderr, "native JSON bundle reader failed: %s contains reflective _type marker\n", path);
                exit(1);
            }
            reject_reflective_type_markers(n->u.obj.items[i].val, n->u.obj.items[i].key);
        }
    } else if (n->kind == N_ARR) {
        for (size_t i = 0; i < n->u.arr.n; i++) {
            reject_reflective_type_markers(n->u.arr.items[i], path);
        }
    }
}

static void validate_section_order(const node *root, const node *sections) {
    const node *order = req_arr(root, "sectionOrder");
    if (order->u.arr.n != SECTION_COUNT) {
        fail("sectionOrder has unexpected length");
    }
    if (sections->u.obj.n != SECTION_COUNT) {
        fail("sections object has unexpected length");
    }
    for (size_t i = 0; i < SECTION_COUNT; i++) {
        expect_string_value(order->u.arr.items[i], SECTION_ORDER[i], "sectionOrder entry");
        if (!str_eq(sections->u.obj.items[i].key, SECTION_ORDER[i])) {
            fail("sections object order does not match schema");
        }
    }
}

static size_t array_count(const node *sections, const char *name) {
    return req_arr(sections, name)->u.arr.n;
}

static void validate_hash_shape(const char *hash) {
    if (!hash || strncmp(hash, "sha256:", 7) != 0 || strlen(hash) != 71) {
        fail("bundleContentHash must be a sha256 hex string");
    }
    for (const char *p = hash + 7; *p; p++) {
        int hex = (*p >= '0' && *p <= '9') ||
                  (*p >= 'a' && *p <= 'f') ||
                  (*p >= 'A' && *p <= 'F');
        if (!hex) {
            fail("bundleContentHash contains non-hex characters");
        }
    }
}

static void validate_commands(const node *commands) {
    expect_kind(commands, N_OBJ, "runtimeSystems.commands");
    if (!obj_get(commands, "wilderness")) {
        fail("runtimeSystems.commands missing wilderness");
    }
    if (!obj_get(commands, "dungeon")) {
        fail("runtimeSystems.commands missing dungeon");
    }
    if (!obj_get(commands, "ydarg.navigation")) {
        fail("runtimeSystems.commands missing ydarg.navigation");
    }
}

static void validate_prompt_groups(const node *prompts) {
    const node *groups = req_arr(prompts, "groups");
    if (!find_obj_by_string_field(groups, "group", "wilderness")) {
        fail("runtimeSystems.prompts missing wilderness group");
    }
    if (!find_obj_by_string_field(groups, "group", "dungeon")) {
        fail("runtimeSystems.prompts missing dungeon group");
    }
}

static void validate_castles(const node *systems) {
    const node *castles = req_arr(systems, "castles");
    const node *ydarg = find_obj_by_string_field(castles, "id", "ydarg");
    if (!ydarg) {
        fail("runtimeSystems.castles missing ydarg");
    }
    if (!str_eq(req_str(ydarg, "kind"), "gridCastle")) {
        fail("runtimeSystems.castles.ydarg kind must be gridCastle");
    }
    (void)req_obj(ydarg, "entry");
    (void)req_arr(ydarg, "roomEvents");
}

static void validate_runtime_systems(const node *sections) {
    const node *systems = req_obj(sections, "runtimeSystems");
    reject_reflective_type_markers(systems, "runtimeSystems");
    if (req_int(systems, "schemaVersion") != RUNTIME_SYSTEMS_SCHEMA_VERSION) {
        fail("runtimeSystems schemaVersion mismatch");
    }
    validate_commands(req_obj(systems, "commands"));
    validate_prompt_groups(req_obj(systems, "prompts"));
    validate_castles(systems);
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <rylween-bundle.json>\n", argv[0]);
        return 2;
    }

    node *root_node = json5_parse_file(argv[1]);
    const node *root = expect_kind(root_node, N_OBJ, "bundle root");
    if (!str_eq(req_str(root, "format"), "rylween.compiled.bundle")) {
        fail("format mismatch");
    }
    if (req_int(root, "schemaVersion") != BUNDLE_SCHEMA_VERSION) {
        fail("bundle schemaVersion mismatch");
    }
    validate_hash_shape(req_str(root, "bundleContentHash"));
    const node *sections = req_obj(root, "sections");
    validate_section_order(root, sections);
    validate_runtime_systems(sections);

    size_t strings = array_count(sections, "strings");
    size_t handles = array_count(sections, "handles");
    size_t scripts = array_count(sections, "scripts");
    size_t states = array_count(sections, "states");
    if (strings == 0 || handles == 0 || scripts == 0 || states == 0) {
        fail("core table sections must not be empty");
    }

    printf("native json ok: strings=%zu handles=%zu scripts=%zu states=%zu runtimeSystems=%d castles=%zu\n",
           strings,
           handles,
           scripts,
           states,
           RUNTIME_SYSTEMS_SCHEMA_VERSION,
           req_arr(req_obj(sections, "runtimeSystems"), "castles")->u.arr.n);

    node_free(root_node);
    return 0;
}
