#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/*
 * TODO(J1/J2/J3): This is only the native compiled-boundary smoke loader.
 *
 * It can parse the narrow .rsmoke artifact emitted from the current compiled
 * bundle and validate schema/version, string ids, handle refs, opcode ranges,
 * nested instruction bodies, typed operands, and state counts. It cannot read
 * the canonical J1 JSON bundle, execute VM ops, run battle/town/wilderness/
 * dungeon/Ydarg sessions, render the game, process input, save/load snapshots,
 * or reproduce the legacy C game's graphical presentation. A real native
 * runtime must implement the J1 JSON reader, J2 op executor, and J3 host/view
 * service contract before it counts as playable.
 */

typedef struct {
    uint32_t kind;
    uint32_t id;
} HandleRef;

enum {
    SMOKE_INVALID_STRING_ID = 0U,
    SMOKE_VERSION = 3U,
    SMOKE_FALSE = 0U,
    SMOKE_TRUE = 1U
};

typedef enum {
    OPERAND_INTEGER = 0U,
    OPERAND_BOOLEAN = 1U,
    OPERAND_CHARACTER = 2U,
    OPERAND_STRING_ID = 3U,
    OPERAND_STRING_ID_LIST = 4U,
    OPERAND_HANDLE = 5U,
    OPERAND_HANDLE_LIST = 6U,
    OPERAND_TEXT_BLOCK = 7U,
    OPERAND_INT_EXPR = 8U,
    OPERAND_BOOL_EXPR = 9U,
    OPERAND_STRING_EXPR = 10U,
    OPERAND_LVALUE = 11U,
    OPERAND_VIEW_COMMAND = 12U,
    OPERAND_INSTRUCTIONS = 13U,
    OPERAND_SWITCH_CASES = 14U,
    OPERAND_MENU_OPTIONS = 15U,
    OPERAND_INPUT_BINDINGS = 16U,
    OPERAND_TEXT_ANSWER = 17U,
    OPERAND_REWARD = 18U,
    OPERAND_RANDOM_ITEM_OFFER = 19U,
    OPERAND_SHOP_SERVICE = 20U,
    OPERAND_ENUM_NAME = 21U
} OperandTag;

typedef enum {
    TEXT_BLOCK_REF = 0U,
    TEXT_BLOCK_INLINE = 1U,
    TEXT_BLOCK_STRUCTURED = 2U,
    TEXT_BLOCK_FLOW = 3U
} TextBlockTag;

typedef enum {
    TEXT_PART_LITERAL = 0U,
    TEXT_PART_INT_VALUE = 1U,
    TEXT_PART_STRING_VALUE = 2U,
    TEXT_PART_ROLE_NAME = 3U,
    TEXT_PART_ROLE_LOCATION = 4U,
    TEXT_PART_TOWN_NAME = 5U,
    TEXT_PART_FUZZY_TOWN_LOCATION = 6U,
    TEXT_PART_LOCATION_COORDINATE = 7U,
    TEXT_PART_RUNTIME = 8U,
    TEXT_PART_ROLE_JOB = 9U,
    TEXT_PART_NPC_NAME = 10U,
    TEXT_PART_NPC_JOB = 11U,
    TEXT_PART_NPC_LOCATION = 12U
} TextPartTag;

typedef enum {
    VIEW_CLEAR_TEXT = 0U,
    VIEW_CLEAR_MAP_WINDOW = 1U,
    VIEW_RENDER_MAP = 2U,
    VIEW_STATUS_LINE = 3U,
    VIEW_SHOW_IMAGE = 4U
} ViewCommandTag;

typedef enum {
    INT_EXPR_LITERAL = 0U,
    INT_EXPR_LOCAL = 1U,
    INT_EXPR_BINARY = 2U,
    INT_EXPR_IF = 3U,
    INT_EXPR_RANDOM = 4U,
    INT_EXPR_MEMBER_FIELD = 5U,
    INT_EXPR_MONSTER_FIELD = 6U,
    INT_EXPR_BATTLE_FLAG = 7U,
    INT_EXPR_BATTLE_QUERY = 8U,
    INT_EXPR_REALM_SCALAR = 9U,
    INT_EXPR_PARTY_SCALAR = 10U,
    INT_EXPR_INVENTORY_POOL_COUNT = 11U,
    INT_EXPR_INVENTORY_SLOT_COST = 12U,
    INT_EXPR_PARTY_MEMBER_QUERY = 13U
} IntExprTag;

typedef enum {
    BOOL_EXPR_CMP = 0U,
    BOOL_EXPR_NOT = 1U,
    BOOL_EXPR_AND = 2U,
    BOOL_EXPR_OR = 3U,
    BOOL_EXPR_FLAG_IS = 4U,
    BOOL_EXPR_MILESTONE_ACHIEVED = 5U,
    BOOL_EXPR_MEMBER_FLAG = 6U,
    BOOL_EXPR_MONSTER_FLAG = 7U,
    BOOL_EXPR_BATTLE_FLAG = 8U,
    BOOL_EXPR_RUNTIME_FLAG = 9U
} BoolExprTag;

typedef enum {
    STRING_EXPR_NATIVE_STRING = 0U
} StringExprTag;

typedef enum {
    NATIVE_STRING_ARG_LITERAL = 0U,
    NATIVE_STRING_ARG_INT_VALUE = 1U
} NativeStringArgTag;

typedef enum {
    LVALUE_REALM_SCALAR = 0U,
    LVALUE_PARTY_SCALAR = 1U,
    LVALUE_MEMBER_FIELD = 2U,
    LVALUE_MONSTER_FIELD = 3U,
    LVALUE_BATTLE_FLAG = 4U
} LValueTag;

typedef enum {
    SHOP_PRICE_ITEM_COST = 0U,
    SHOP_PRICE_HALF_ITEM_COST = 1U,
    SHOP_PRICE_FIXED = 2U,
    SHOP_PRICE_LEADER_LEVEL_PLUS = 3U,
    SHOP_PRICE_ITEM_COST_PLUS_LEADER_LEVEL_TIMES = 4U
} ShopPriceTag;

typedef enum {
    SHOP_PURCHASE_INVENTORY_ITEM = 0U,
    SHOP_PURCHASE_BULK = 1U
} ShopPurchaseTag;

typedef enum {
    SHOP_QUANTITY_CAP_FIXED_AMOUNT = 0U,
    SHOP_QUANTITY_CAP_CARRY_CAPACITY = 1U
} ShopQuantityCapTag;

typedef struct {
    uint8_t *data;
    size_t size;
    size_t pos;
    uint32_t handle_kind_count;
    uint32_t opcode_count;
    uint32_t runtime_text_part_count;
    uint32_t string_count;
    uint32_t handle_count;
    HandleRef *handles;
    uint64_t instruction_count;
    uint64_t operand_count;
} Reader;

static void fail_at(const Reader *reader, const char *message) {
    fprintf(stderr, "native smoke loader failed at byte %zu: %s\n", reader->pos, message);
    exit(1);
}

static void require_available(Reader *reader, size_t count) {
    if (reader->pos > reader->size || count > reader->size - reader->pos) {
        fail_at(reader, "unexpected end of file");
    }
}

static uint32_t read_u32(Reader *reader) {
    require_available(reader, 4);
    uint32_t value = (uint32_t)reader->data[reader->pos]
        | ((uint32_t)reader->data[reader->pos + 1] << 8)
        | ((uint32_t)reader->data[reader->pos + 2] << 16)
        | ((uint32_t)reader->data[reader->pos + 3] << 24);
    reader->pos += 4;
    return value;
}

static void read_bool(Reader *reader) {
    uint32_t value = read_u32(reader);
    if (value != SMOKE_FALSE && value != SMOKE_TRUE) {
        fail_at(reader, "boolean value is not 0/1");
    }
}

static int read_optional_present(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present != SMOKE_FALSE && present != SMOKE_TRUE) {
        fail_at(reader, "optional marker is not 0/1");
    }
    return present == SMOKE_TRUE;
}

static void skip_bytes(Reader *reader, uint32_t count) {
    require_available(reader, count);
    reader->pos += count;
}

static void read_inline_string(Reader *reader) {
    skip_bytes(reader, read_u32(reader));
}

static void read_string_ref(Reader *reader) {
    uint32_t id = read_u32(reader);
    if (id == SMOKE_INVALID_STRING_ID || id > reader->string_count) {
        fail_at(reader, "string id is out of range");
    }
}

static int handle_exists(const Reader *reader, HandleRef ref) {
    for (uint32_t i = 0; i < reader->handle_count; ++i) {
        if (reader->handles[i].kind == ref.kind && reader->handles[i].id == ref.id) {
            return 1;
        }
    }
    return 0;
}

static HandleRef read_handle_ref(Reader *reader) {
    HandleRef ref;
    ref.kind = read_u32(reader);
    ref.id = read_u32(reader);
    if (ref.kind >= reader->handle_kind_count) {
        fail_at(reader, "handle kind is out of range");
    }
    if (!handle_exists(reader, ref)) {
        fail_at(reader, "handle ref is not declared");
    }
    return ref;
}

static void parse_instructions(Reader *reader);
static void parse_text_block(Reader *reader);
static void parse_int_expr(Reader *reader);
static void parse_bool_expr(Reader *reader);
static void parse_string_expr(Reader *reader);
static void parse_lvalue(Reader *reader);
static void parse_view_command(Reader *reader);
static void parse_input_binding(Reader *reader);
static void parse_script_reward(Reader *reader);
static void parse_random_item_offer(Reader *reader);
static void parse_shop_service(Reader *reader);

static void parse_string_id_list(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        read_string_ref(reader);
    }
}

static void parse_handle_ref_list(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        (void)read_handle_ref(reader);
    }
}

static void parse_optional_int(Reader *reader) {
    if (read_optional_present(reader)) {
        (void)read_u32(reader);
    }
}

static void parse_optional_string_ref(Reader *reader) {
    if (read_optional_present(reader)) {
        read_string_ref(reader);
    }
}

static void parse_optional_handle_ref(Reader *reader) {
    if (read_optional_present(reader)) {
        (void)read_handle_ref(reader);
    }
}

static void parse_optional_text_block(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_text_block(reader);
    }
}

static void parse_optional_int_expr(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_int_expr(reader);
    }
}

static void parse_enum_name(Reader *reader) {
    read_inline_string(reader);
}

static void parse_reward_application(Reader *reader) {
    parse_enum_name(reader);
    parse_enum_name(reader);
    parse_enum_name(reader);
}

static void parse_reward_messages(Reader *reader) {
    parse_optional_text_block(reader);
    parse_optional_text_block(reader);
}

static void parse_text_part(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case TEXT_PART_LITERAL: read_string_ref(reader); break;
        case TEXT_PART_INT_VALUE: parse_int_expr(reader); break;
        case TEXT_PART_STRING_VALUE: parse_string_expr(reader); break;
        case TEXT_PART_ROLE_NAME:
        case TEXT_PART_ROLE_LOCATION:
        case TEXT_PART_TOWN_NAME:
        case TEXT_PART_LOCATION_COORDINATE:
        case TEXT_PART_ROLE_JOB:
        case TEXT_PART_NPC_NAME:
        case TEXT_PART_NPC_JOB:
        case TEXT_PART_NPC_LOCATION:
            (void)read_handle_ref(reader);
            break;
        case TEXT_PART_FUZZY_TOWN_LOCATION:
            (void)read_handle_ref(reader);
            (void)read_u32(reader);
            break;
        case TEXT_PART_RUNTIME: {
            uint32_t runtime_part = read_u32(reader);
            if (runtime_part >= reader->runtime_text_part_count) {
                fail_at(reader, "runtime text part is out of range");
            }
            break;
        }
        default:
            fail_at(reader, "unknown text part tag");
    }
}

static void parse_text_line(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_text_part(reader);
    }
}

static void parse_text_block(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case TEXT_BLOCK_REF:
            (void)read_handle_ref(reader);
            break;
        case TEXT_BLOCK_INLINE:
            parse_string_id_list(reader);
            break;
        case TEXT_BLOCK_STRUCTURED:
        case TEXT_BLOCK_FLOW: {
            uint32_t count = read_u32(reader);
            for (uint32_t i = 0; i < count; ++i) {
                parse_text_line(reader);
            }
            break;
        }
        default:
            fail_at(reader, "unknown text block tag");
    }
}

static void parse_view_command(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case VIEW_CLEAR_TEXT:
        case VIEW_CLEAR_MAP_WINDOW:
        case VIEW_RENDER_MAP:
        case VIEW_STATUS_LINE:
            break;
        case VIEW_SHOW_IMAGE:
            (void)read_handle_ref(reader);
            break;
        default:
            fail_at(reader, "unknown view command tag");
    }
}

static void parse_int_expr(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case INT_EXPR_LITERAL:
            (void)read_u32(reader);
            break;
        case INT_EXPR_LOCAL:
        case INT_EXPR_BATTLE_FLAG:
        case INT_EXPR_BATTLE_QUERY:
        case INT_EXPR_REALM_SCALAR:
        case INT_EXPR_PARTY_SCALAR:
            read_string_ref(reader);
            break;
        case INT_EXPR_BINARY:
            parse_int_expr(reader);
            parse_enum_name(reader);
            parse_int_expr(reader);
            break;
        case INT_EXPR_IF:
            parse_bool_expr(reader);
            parse_int_expr(reader);
            parse_int_expr(reader);
            break;
        case INT_EXPR_RANDOM:
            parse_int_expr(reader);
            parse_int_expr(reader);
            break;
        case INT_EXPR_MEMBER_FIELD:
        case INT_EXPR_MONSTER_FIELD:
            parse_int_expr(reader);
            read_string_ref(reader);
            break;
        case INT_EXPR_INVENTORY_POOL_COUNT:
            (void)read_handle_ref(reader);
            break;
        case INT_EXPR_INVENTORY_SLOT_COST:
            parse_int_expr(reader);
            break;
        case INT_EXPR_PARTY_MEMBER_QUERY:
            parse_enum_name(reader);
            parse_enum_name(reader);
            break;
        default:
            fail_at(reader, "unknown int expr tag");
    }
}

static void parse_bool_expr(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case BOOL_EXPR_CMP:
            parse_int_expr(reader);
            parse_enum_name(reader);
            parse_int_expr(reader);
            break;
        case BOOL_EXPR_NOT:
            parse_bool_expr(reader);
            break;
        case BOOL_EXPR_AND:
        case BOOL_EXPR_OR:
            parse_bool_expr(reader);
            parse_bool_expr(reader);
            break;
        case BOOL_EXPR_FLAG_IS:
            (void)read_handle_ref(reader);
            (void)read_handle_ref(reader);
            break;
        case BOOL_EXPR_MILESTONE_ACHIEVED:
            (void)read_handle_ref(reader);
            break;
        case BOOL_EXPR_MEMBER_FLAG:
        case BOOL_EXPR_MONSTER_FLAG:
            parse_int_expr(reader);
            read_string_ref(reader);
            break;
        case BOOL_EXPR_BATTLE_FLAG:
        case BOOL_EXPR_RUNTIME_FLAG:
            read_string_ref(reader);
            break;
        default:
            fail_at(reader, "unknown bool expr tag");
    }
}

static void parse_native_string_arg(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case NATIVE_STRING_ARG_LITERAL:
            read_string_ref(reader);
            break;
        case NATIVE_STRING_ARG_INT_VALUE:
            parse_int_expr(reader);
            break;
        default:
            fail_at(reader, "unknown native string arg tag");
    }
}

static void parse_string_expr(Reader *reader) {
    uint32_t tag = read_u32(reader);
    if (tag != STRING_EXPR_NATIVE_STRING) {
        fail_at(reader, "unknown string expr tag");
    }
    read_string_ref(reader);
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_native_string_arg(reader);
    }
}

static void parse_lvalue(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case LVALUE_REALM_SCALAR:
        case LVALUE_PARTY_SCALAR:
        case LVALUE_BATTLE_FLAG:
            read_string_ref(reader);
            break;
        case LVALUE_MEMBER_FIELD:
        case LVALUE_MONSTER_FIELD:
            parse_int_expr(reader);
            read_string_ref(reader);
            break;
        default:
            fail_at(reader, "unknown lvalue tag");
    }
}

static void parse_input_binding(Reader *reader) {
    read_string_ref(reader);
    parse_string_id_list(reader);
    read_string_ref(reader);
}

static void parse_optional_input_binding(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_input_binding(reader);
    }
}

static void parse_switch_cases(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_optional_int(reader);
        parse_instructions(reader);
    }
}

static void parse_menu_options(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_input_binding(reader);
        parse_instructions(reader);
    }
}

static void parse_input_bindings(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_input_binding(reader);
    }
}

static void parse_text_answer(Reader *reader) {
    parse_string_id_list(reader);
    parse_instructions(reader);
    parse_instructions(reader);
    read_bool(reader);
    read_bool(reader);
    parse_optional_int(reader);
}

static void parse_script_reward(Reader *reader) {
    parse_optional_int_expr(reader);
    parse_handle_ref_list(reader);
    parse_reward_application(reader);
    parse_reward_messages(reader);
}

static void parse_random_item_offer(Reader *reader) {
    (void)read_handle_ref(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_optional_text_block(reader);
    parse_reward_application(reader);
    parse_reward_messages(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_optional_int(reader);
    (void)read_u32(reader);
    (void)read_u32(reader);
    (void)read_u32(reader);
    (void)read_u32(reader);
}

static void parse_shop_price(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case SHOP_PRICE_ITEM_COST:
        case SHOP_PRICE_HALF_ITEM_COST:
            break;
        case SHOP_PRICE_FIXED:
        case SHOP_PRICE_LEADER_LEVEL_PLUS:
        case SHOP_PRICE_ITEM_COST_PLUS_LEADER_LEVEL_TIMES:
            (void)read_u32(reader);
            break;
        default:
            fail_at(reader, "unknown shop price tag");
    }
}

static void parse_shop_quantity_cap(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case SHOP_QUANTITY_CAP_FIXED_AMOUNT:
            parse_enum_name(reader);
            (void)read_u32(reader);
            break;
        case SHOP_QUANTITY_CAP_CARRY_CAPACITY:
            parse_enum_name(reader);
            break;
        default:
            fail_at(reader, "unknown shop quantity cap tag");
    }
}

static void parse_shop_purchase(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case SHOP_PURCHASE_INVENTORY_ITEM:
            break;
        case SHOP_PURCHASE_BULK:
            parse_enum_name(reader);
            parse_text_block(reader);
            parse_shop_quantity_cap(reader);
            parse_text_block(reader);
            parse_enum_name(reader);
            break;
        default:
            fail_at(reader, "unknown shop purchase tag");
    }
}

static void parse_shop_item_offer(Reader *reader) {
    parse_input_binding(reader);
    (void)read_handle_ref(reader);
    parse_optional_string_ref(reader);
    parse_optional_text_block(reader);
    parse_shop_price(reader);
    parse_shop_purchase(reader);
}

static void parse_shop_catalog_page(Reader *reader) {
    uint32_t entry_count = read_u32(reader);
    for (uint32_t i = 0; i < entry_count; ++i) {
        parse_shop_item_offer(reader);
    }
    parse_optional_text_block(reader);
    parse_optional_input_binding(reader);
    parse_optional_input_binding(reader);
}

static void parse_shop_catalog(Reader *reader) {
    parse_input_binding(reader);
    read_string_ref(reader);
    parse_optional_text_block(reader);
    uint32_t page_count = read_u32(reader);
    for (uint32_t i = 0; i < page_count; ++i) {
        parse_shop_catalog_page(reader);
    }
}

static void parse_shop_buy(Reader *reader) {
    parse_text_block(reader);
    uint32_t catalog_count = read_u32(reader);
    for (uint32_t i = 0; i < catalog_count; ++i) {
        parse_shop_catalog(reader);
    }
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_optional_text_block(reader);
    parse_optional_text_block(reader);
    parse_optional_text_block(reader);
    parse_text_block(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_reward_application(reader);
}

static void parse_shop_haggle(Reader *reader) {
    uint32_t bonus_count = read_u32(reader);
    for (uint32_t i = 0; i < bonus_count; ++i) {
        (void)read_u32(reader);
        (void)read_u32(reader);
    }
    parse_optional_int(reader);
}

static void parse_optional_shop_haggle(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_shop_haggle(reader);
    }
}

static void parse_shop_sell(Reader *reader) {
    parse_text_block(reader);
    uint32_t accepted_kind_count = read_u32(reader);
    for (uint32_t i = 0; i < accepted_kind_count; ++i) {
        parse_enum_name(reader);
    }
    parse_handle_ref_list(reader);
    parse_handle_ref_list(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_optional_text_block(reader);
    parse_optional_text_block(reader);
    parse_shop_price(reader);
    parse_optional_shop_haggle(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
}

static void parse_shop_interlude(Reader *reader) {
    (void)read_u32(reader);
    parse_text_block(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    uint32_t accepted_count = read_u32(reader);
    for (uint32_t i = 0; i < accepted_count; ++i) {
        parse_text_block(reader);
    }
    parse_text_block(reader);
    read_bool(reader);
    parse_optional_handle_ref(reader);
}

static void parse_optional_shop_buy(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_shop_buy(reader);
    }
}

static void parse_optional_shop_sell(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_shop_sell(reader);
    }
}

static void parse_optional_shop_interlude(Reader *reader) {
    if (read_optional_present(reader)) {
        parse_shop_interlude(reader);
    }
}

static void parse_shop_service(Reader *reader) {
    (void)read_handle_ref(reader);
    (void)read_handle_ref(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_text_block(reader);
    parse_optional_shop_buy(reader);
    parse_optional_shop_sell(reader);
    parse_optional_shop_interlude(reader);
}

static void parse_operand(Reader *reader) {
    ++reader->operand_count;
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case OPERAND_INTEGER:
            (void)read_u32(reader);
            break;
        case OPERAND_BOOLEAN:
            read_bool(reader);
            break;
        case OPERAND_CHARACTER:
            (void)read_u32(reader);
            break;
        case OPERAND_STRING_ID:
            read_string_ref(reader);
            break;
        case OPERAND_STRING_ID_LIST:
            parse_string_id_list(reader);
            break;
        case OPERAND_HANDLE:
            (void)read_handle_ref(reader);
            break;
        case OPERAND_HANDLE_LIST:
            parse_handle_ref_list(reader);
            break;
        case OPERAND_TEXT_BLOCK:
            parse_text_block(reader);
            break;
        case OPERAND_INT_EXPR:
            parse_int_expr(reader);
            break;
        case OPERAND_BOOL_EXPR:
            parse_bool_expr(reader);
            break;
        case OPERAND_STRING_EXPR:
            parse_string_expr(reader);
            break;
        case OPERAND_LVALUE:
            parse_lvalue(reader);
            break;
        case OPERAND_VIEW_COMMAND:
            parse_view_command(reader);
            break;
        case OPERAND_INSTRUCTIONS:
            parse_instructions(reader);
            break;
        case OPERAND_SWITCH_CASES:
            parse_switch_cases(reader);
            break;
        case OPERAND_MENU_OPTIONS:
            parse_menu_options(reader);
            break;
        case OPERAND_INPUT_BINDINGS:
            parse_input_bindings(reader);
            break;
        case OPERAND_TEXT_ANSWER:
            parse_text_answer(reader);
            break;
        case OPERAND_REWARD:
            parse_script_reward(reader);
            break;
        case OPERAND_RANDOM_ITEM_OFFER:
            parse_random_item_offer(reader);
            break;
        case OPERAND_SHOP_SERVICE:
            parse_shop_service(reader);
            break;
        case OPERAND_ENUM_NAME:
            read_inline_string(reader);
            break;
        default:
            fail_at(reader, "unknown operand tag");
    }
}

static void parse_instructions(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        uint32_t opcode = read_u32(reader);
        if (opcode >= reader->opcode_count) {
            fail_at(reader, "opcode is out of range");
        }
        ++reader->instruction_count;
        uint32_t operand_count = read_u32(reader);
        for (uint32_t j = 0; j < operand_count; ++j) {
            parse_operand(reader);
        }
    }
}

static uint8_t *read_file(const char *path, size_t *size_out) {
    FILE *file = fopen(path, "rb");
    if (file == NULL) {
        fprintf(stderr, "cannot open %s\n", path);
        exit(1);
    }
    if (fseek(file, 0, SEEK_END) != 0) {
        fprintf(stderr, "cannot seek %s\n", path);
        exit(1);
    }
    long size = ftell(file);
    if (size < 0) {
        fprintf(stderr, "cannot size %s\n", path);
        exit(1);
    }
    rewind(file);
    uint8_t *data = malloc((size_t)size);
    if (data == NULL && size > 0) {
        fprintf(stderr, "out of memory\n");
        exit(1);
    }
    if (fread(data, 1, (size_t)size, file) != (size_t)size) {
        fprintf(stderr, "cannot read %s\n", path);
        exit(1);
    }
    fclose(file);
    *size_out = (size_t)size;
    return data;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <rylween-native-smoke.rsmoke>\n", argv[0]);
        return 2;
    }

    Reader reader;
    memset(&reader, 0, sizeof(reader));
    reader.data = read_file(argv[1], &reader.size);

    const char magic[] = "RYLSMOKE";
    require_available(&reader, sizeof(magic) - 1);
    if (memcmp(reader.data, magic, sizeof(magic) - 1) != 0) {
        fail_at(&reader, "bad magic");
    }
    reader.pos += sizeof(magic) - 1;

    uint32_t version = read_u32(&reader);
    if (version != SMOKE_VERSION) {
        fail_at(&reader, "unsupported artifact version");
    }
    reader.handle_kind_count = read_u32(&reader);
    reader.opcode_count = read_u32(&reader);
    reader.runtime_text_part_count = read_u32(&reader);

    reader.string_count = read_u32(&reader);
    for (uint32_t i = 0; i < reader.string_count; ++i) {
        skip_bytes(&reader, read_u32(&reader));
    }

    reader.handle_count = read_u32(&reader);
    reader.handles = calloc(reader.handle_count, sizeof(HandleRef));
    if (reader.handles == NULL && reader.handle_count > 0U) {
        fail_at(&reader, "out of memory");
    }
    for (uint32_t i = 0; i < reader.handle_count; ++i) {
        HandleRef ref;
        ref.kind = read_u32(&reader);
        ref.id = read_u32(&reader);
        if (ref.kind >= reader.handle_kind_count) {
            fail_at(&reader, "declared handle kind is out of range");
        }
        reader.handles[i] = ref;
        (void)read_u32(&reader);
        read_string_ref(&reader);
    }

    uint32_t script_count = read_u32(&reader);
    for (uint32_t i = 0; i < script_count; ++i) {
        (void)read_handle_ref(&reader);
        parse_instructions(&reader);
    }

    uint32_t state_count = read_u32(&reader);
    if (reader.pos != reader.size) {
        fail_at(&reader, "trailing bytes");
    }

    printf(
        "native smoke ok: strings=%u handles=%u scripts=%u states=%u instructions=%llu operands=%llu\n",
        reader.string_count,
        reader.handle_count,
        script_count,
        state_count,
        (unsigned long long)reader.instruction_count,
        (unsigned long long)reader.operand_count
    );

    free(reader.handles);
    free(reader.data);
    return 0;
}
