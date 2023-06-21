import json
from time import time

from flask import Response, abort, request

from constants import (ACTIVITY_TABLE_URL, ANNOUNCEMENT_META_PATH,
                       BUILDING_DATA_URL, CHARACTER_TABLE_URL,
                       CHARWORD_TABLE_URL, CONFIG_PATH, EQUIP_TABLE_URL,
                       GACHA_TABLE_URL, GAMEDATA_CONST_URL, ITEM_TABLE_URL,
                       MEDAL_TABLE_URL, RL_TABLE_URL, SHOP_CLIENT_TABLE_URL,
                       SKIN_TABLE_URL, STAGE_TABLE_URL,
                       SYNC_DATA_TEMPLATE_PATH, TOWER_TABLE_URL)
from core.Account import Account
from core.database import userData
from core.function.unlockActivity import unlockActivity
from core.function.update import updateData
from utils import read_json


public class Payment {
    public static void main(String[] args) {
        Gson gson = new Gson();

        // Routes
        post("/pay/getUnconfirmedOrderIdList", (request, response) -> {
            logInfo("Hit /pay/getUnconfirmedOrderIdList", request.headers("X-Forwarded-For"));

            Map<String, Object> resp = new HashMap<>();
            resp.put("orderIdList", new String[]{});
            resp.put("playerDataDelta", new HashMap<>());
            
            return gson.toJson(resp);
        });

        post("/pay/createOrder", (request, response) -> {
            logInfo("Hit /pay/createOrder", request.headers("X-Forwarded-For"));

            Map<String, Object> resp = new HashMap<>();
            resp.put("alertMinor", 0);
            resp.put("extension", "");
            resp.put("orderId", "20770230191981000063369555114514");
            resp.put("playerDataDelta", new HashMap<>());
            resp.put("result", 0);

            return gson.toJson(resp);
        });

        post("/u8/pay/confirmOrderState", (request, response) -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("payState", 3);

            return gson.toJson(resp);
        });

        post("/pay/confirmOrderAlipay", (request, response) -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", 0);

            return gson.toJson(resp);
        });

        post("/pay/confirmOrder", (request, response) -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("goodId", "GP_Once_1");
            Map<String, Object> playerDataDelta = new HashMap<>();
            playerDataDelta.put("deleted", new HashMap<>());
            Map<String, Object> modified = new HashMap<>();
            modified.put("status", new HashMap<>());
            playerDataDelta.put("modified", modified);
            resp.put("playerDataDelta", playerDataDelta);
            Map<String, Object> receiveItems = new HashMap<>();
            receiveItems.put("items", new Object[]{});
            resp.put("receiveItems", receiveItems);
            resp.put("result", 0);

            return gson.toJson(resp);
        });
    }

    private static void logInfo(String message, String ipAddress) {
        System.out.println("[" + ipAddress + "] " + message);
    }
}

def userTimestamp() -> int:

    time_now = int(time())
    server_config = read_json(CONFIG_PATH)
    user_ts = server_config["developer"]["timestamp"]

    if user_ts == -1 or user_ts > time_now:
        timestamp = time_now
    else:
        timestamp = user_ts

    return timestamp


def accountLogin() -> Response:
    '''
    result:
        1：此账号禁止登入游戏，详情请咨询客服
        2：当前客户端版本已过时，将检测最新客户端
        3：记忆已经模糊，清重新输入登录信息
        4：数据文件已过期，请重新登录
        5：网络配置已过期，请重新登录
        6：账号时间信息失去同步，请确认账号信息后重试
    '''

    data = request.data
    request_data = request.get_json()

    secret = request_data["token"]
    clientVersion = request_data["clientVersion"]
    networkVersion = str(request_data["networkVersion"])

    result = userData.query_account_by_secret(secret)

    if len(result) != 1:
        data = {
            "result": 3
        }
        return data

    accounts = Account(*result[0])
    server_config = read_json(CONFIG_PATH)
    player_data = json.loads(accounts.get_user())

    if accounts.get_ban() == 1:
        data = {
            "result": 1
        }
        return data

    if clientVersion != server_config["version"]["android"]["clientVersion"]:
        data = {
            "result": 2
        }
        return data

    if networkVersion != server_config["networkConfig"]["content"]["configVer"]:
        data = {
            "result": 5
        }
        return data

    try:
        if server_config["developer"]["timestamp"] > int(time()):
            data = {
                "result": 6
            }
            return data

    except Exception:
        data = {
            "result": 4
        }
        return data

    if accounts.get_user() == "{}":
        ts = int(time())
        syncData = read_json(SYNC_DATA_TEMPLATE_PATH, encoding='utf8')
        syncData["status"]["registerTs"] = ts
        syncData["status"]["lastApAddTime"] = ts

        userData.set_user_data(accounts.get_uid(), syncData)

    if "checkMeta" not in player_data:
        player_data["checkMeta"] = {
            "version": 65230,
            "ts": 1618436227
        }

    data = {
        "result": 0,
        "uid": accounts.get_uid(),
        "secret": secret,
        "serviceLicenseVersion": 0
    }

    return data


def accountSyncData() -> Response:
    '''
    result:
        1：账号时间信息失去同步，请确认账号信息后重试
    '''

    data = request.data

    secret = request.headers.get("secret")
    server_config = read_json(CONFIG_PATH)

    if not server_config["server"]["enableServer"]:
        return abort(400)

    result = userData.query_account_by_secret(secret)

    if len(result) != 1:
        return abort(500)

    ts = userTimestamp()
    accounts = Account(*result[0])
    player_data = json.loads(accounts.get_user())

    player_data["status"]["lastOnlineTs"] = int(time())
    player_data["status"]["lastRefreshTs"] = ts
    player_data.setdefault("carousel", {})

    updateData(ACTIVITY_TABLE_URL)
    updateData(CHARACTER_TABLE_URL)
    updateData(CHARWORD_TABLE_URL)
    updateData(EQUIP_TABLE_URL)
    updateData(RL_TABLE_URL)
    updateData(GACHA_TABLE_URL)
    updateData(ITEM_TABLE_URL)
    updateData(STAGE_TABLE_URL)
    updateData(MEDAL_TABLE_URL)
    updateData(SKIN_TABLE_URL)
    updateData(TOWER_TABLE_URL)
    updateData(SHOP_CLIENT_TABLE_URL)
    updateData(GAMEDATA_CONST_URL)
    updateData(BUILDING_DATA_URL)

    unlockActivity(player_data)
    userData.set_user_data(accounts.get_uid(), player_data)

    data = {
        "result": 0,
        "ts": ts,
        "user": player_data
    }

    return data


def accountSyncStatus() -> Response:

    data = request.data
    request_data = request.get_json()

    secret = request.headers.get("secret")
    params = request_data["params"]
    server_config = read_json(CONFIG_PATH)

    if not server_config["server"]["enableServer"]:
        return abort(400)

    result = userData.query_account_by_secret(secret)

    if len(result) != 1:
        return abort(500)

    ts = userTimestamp()
    accounts = Account(*result[0])

    player_data = json.loads(accounts.get_user())
    player_data["status"]["lastOnlineTs"] = int(time())
    player_data["status"]["lastRefreshTs"] = ts
    player_data["pushFlags"]["hasGifts"] = 0
    player_data["pushFlags"]["hasFriendRequest"] = 0

    # Check consumable
    consumable = player_data["consumable"]

    for index in list(consumable.keys()):
        for item in list(consumable[index].keys()):
            ES = consumable[index][item]
            if ES["ts"] != -1:
                if ES["ts"] <= int(time()) or ES["count"] == 0:
                    del consumable[index][item]

    # Mail
    mailbox_list = json.loads(accounts.get_mails())

    for index in range(len(mailbox_list)):
        if mailbox_list[index]["state"] == 0:
            if int(time()) <= mailbox_list[index]["expireAt"]:
                player_data["pushFlags"]["hasGifts"] = 1
                break
            else:
                mailbox_list[index]["remove"] = 1

    # Friends
    friend_data = json.loads(accounts.get_friend())
    friend_request = friend_data["request"]

    for friend in friend_request:
        result = userData.query_account_by_uid(friend["uid"])
        if len(result) == 0:
            friend_request.remove(friend)

    userData.set_friend_data(accounts.get_uid(), friend_data)

    if len(friend_request) != 0:
        player_data["pushFlags"]["hasFriendRequest"] = 1

    userData.set_user_data(accounts.get_uid(), player_data)

    # Announcement
    announcementVersion = read_json(ANNOUNCEMENT_META_PATH, encoding='utf-8')["focusAnnounceId"]
    announcementPopUpVersion = server_config["version"]["android"]["resVersion"][:5].replace("-", "") + str(accounts.get_uid())[-4:]

    modules = {}

    for key, value in params.items():
        if key == "16":
            modules.setdefault("16", {
                "goodPurchaseState": {
                    "result": {}
                }
            })
            goodIdMap = value["goodIdMap"]
            for goodType in goodIdMap:
                if goodType == "GP":
                    good_list = {}
                    for type in list(player_data["shop"]["GP"].keys()):
                        good_list.update({d["id"]: d["count"] for d in player_data["shop"]["GP"][type]["info"]})
                else:
                    good_list = {d["id"]: d["count"] for d in player_data["shop"][goodType]["info"]} if goodType != "CASH" else player_data["shop"]["FURNI"].get("groupInfo", {})
                for item in goodIdMap[goodType]:
                    if item in good_list:
                        modules["16"]["goodPurchaseState"]["result"].update({item: -1})
                    else:
                        modules["16"]["goodPurchaseState"]["result"].update({item: 1})

    data = {
        "ts": ts,
        "result": {
            "4": {
                "announcementVersion": announcementVersion,
                "announcementPopUpVersion": announcementPopUpVersion
            }
        },
        "playerDataDelta": {
            "modified": {
                "status": player_data["status"],
                "gacha": player_data["gacha"],
                "inventory": player_data["inventory"],
                "pushFlags": player_data["pushFlags"],
                "building": player_data["building"],
                "carousel": player_data["carousel"],
                "consumable": player_data["consumable"],
                "event": player_data["event"],
                "retro": player_data["retro"],
                "rlv2": player_data["rlv2"]
            },
            "deleted": {}
        }
    }

    if len(modules) > 0:
        data["result"].update(modules)

    return data