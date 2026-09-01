package cn.chrelyonly.chrome.config;

public class DyConfig {

    public static String injectJsDyVideo = "\n" +
            "(function () {\n" +
            "    'use strict';\n" +
            "\n" +
            "    // \uD83D\uDED1 核心修复：防止脚本在 iframe 中重复执行，避免日志打印多遍\n" +
            "    if (window.top !== window.self) {\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    // ⚙\uFE0F 全局配置中心\n" +
            "    const CONFIG = {\n" +
            "        // 1. 需要隐藏/屏蔽的 CSS 类名与选择器 (页面净化)\n" +
            "        BLOCK_CLASSES: [\n" +
            "            'PizujT86', 'rOzrkmJZ', 'iIM5THgi', 'G_zSMS6O',\n" +
            "            'GP4F3oOp', 'Ii031XNo', 'GjgTnTzB', 'IRvWiuKO', 'detailPage'\n" +
            "        ],\n" +
            "        BLOCK_SELECTORS: [],\n" +
            "        // 2. UI 交互配置\n" +
            "        UI: {\n" +
            "            HOT_KEY: 'v' // 按下此键触发控制台打印\n" +
            "        },\n" +
            "        // 3. DOM 选择器映射\n" +
            "        SELECTORS: {\n" +
            "            VIDEO_CONTAINER: '[data-e2e=\"feed-active-video\"], [data-e2e=\"video-detail\"]',\n" +
            "            VIDEO_TITLE: '[data-e2e=\"video-desc\"], .title--text, h1',\n" +
            "            USER_INFO: '[data-e2e=\"user-info\"], .SbLFL11J',\n" +
            "            COMMENT_ITEM: '[data-e2e=\"comment-item\"]',\n" +
            "            COMMENT_AVATAR: '[data-e2e=\"comment-avatar\"] img, .comment-item-avatar img',\n" +
            "            COMMENT_NICKNAME: '[data-e2e=\"comment-nickname\"], .meTuPoyz, .vlTNLkdG a',\n" +
            "            COMMENT_CONTENT: '[data-e2e=\"comment-content\"], .FduGc_lz, .Sh1Da424',\n" +
            "            COMMENT_TIME_LOC: '[data-e2e=\"comment-time\"], .VAQA49VP',\n" +
            "            COMMENT_LIKE: '[data-e2e=\"comment-like-count\"], .a2ls69qS .VpA2NKl1 span'\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    // \uD83C\uDF1F 控制台日志封装\n" +
            "    const Logger = {\n" +
            "        info: (msg, ...args) => console.log(`%c \uD83C\uDFB5 [抖音提取器] ℹ\uFE0F ${msg}`, 'color: #409EFF; font-weight: bold;', ...args),\n" +
            "        success: (msg, ...args) => console.log(`%c \uD83C\uDFB5 [抖音提取器] ✅ ${msg}`, 'color: #67C23A; font-weight: bold;', ...args),\n" +
            "        warn: (msg, ...args) => console.warn(`%c \uD83C\uDFB5 [抖音提取器] ⚠\uFE0F ${msg}`, 'color: #E6A23C; font-weight: bold;', ...args),\n" +
            "        error: (msg, ...args) => console.error(`%c \uD83C\uDFB5 [抖音提取器] ❌ ${msg}`, 'color: #F56C6C; font-weight: bold;', ...args)\n" +
            "    };\n" +
            "\n" +
            "    // \uD83D\uDEE0\uFE0F 页面净化 CSS 注入 (只负责隐藏广告与多余区域)\n" +
            "    function applyPureStyle() {\n" +
            "        const classRules = CONFIG.BLOCK_CLASSES.map(cls => `.${cls}`).join(',\\n');\n" +
            "        const selectorRules = CONFIG.BLOCK_SELECTORS.join(',\\n');\n" +
            "        const combined = [classRules, selectorRules].filter(Boolean).join(',\\n');\n" +
            "        const blockCss = combined ? `${combined} { display: none !important; }` : '';\n" +
            "\n" +
            "        if (!blockCss) return;\n" +
            "\n" +
            "        if (typeof GM_addStyle !== 'undefined') {\n" +
            "            GM_addStyle(blockCss);\n" +
            "        } else {\n" +
            "            const styleNode = document.createElement('style');\n" +
            "            styleNode.textContent = blockCss;\n" +
            "            document.head.appendChild(styleNode);\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    applyPureStyle();\n" +
            "    Logger.info(`脚本初始化启动... 页面净化生效中，按 [${CONFIG.UI.HOT_KEY.toUpperCase()}] 键提取数据到 Console \uD83D\uDE80`);\n" +
            "\n" +
            "    // \uD83D\uDCAC 解析评论列表\n" +
            "    function getCommentList(limit = 10) {\n" +
            "        try {\n" +
            "            const commentNodes = document.querySelectorAll(CONFIG.SELECTORS.COMMENT_ITEM);\n" +
            "            if (!commentNodes || commentNodes.length === 0) return [];\n" +
            "\n" +
            "            const comments = [];\n" +
            "            const targetNodes = Array.from(commentNodes).slice(0, limit);\n" +
            "\n" +
            "            targetNodes.forEach(node => {\n" +
            "                const avatarImg = node.querySelector(CONFIG.SELECTORS.COMMENT_AVATAR);\n" +
            "                const nickElem = node.querySelector(CONFIG.SELECTORS.COMMENT_NICKNAME);\n" +
            "                const contentElem = node.querySelector(CONFIG.SELECTORS.COMMENT_CONTENT);\n" +
            "                const timeLocElem = node.querySelector(CONFIG.SELECTORS.COMMENT_TIME_LOC);\n" +
            "                const likeElem = node.querySelector(CONFIG.SELECTORS.COMMENT_LIKE);\n" +
            "\n" +
            "                let contentText = '';\n" +
            "                if (contentElem) {\n" +
            "                    const clone = contentElem.cloneNode(true);\n" +
            "                    clone.querySelectorAll('img').forEach(img => {\n" +
            "                        img.replaceWith(document.createTextNode(img.alt || '[表情]'));\n" +
            "                    });\n" +
            "                    contentText = clone.innerText.trim();\n" +
            "                }\n" +
            "\n" +
            "                comments.push({\n" +
            "                    avatar: avatarImg ? avatarImg.src : '',\n" +
            "                    nickname: nickElem ? nickElem.innerText.trim() : '匿名用户',\n" +
            "                    content: contentText,\n" +
            "                    timeLocation: timeLocElem ? timeLocElem.innerText.trim() : '',\n" +
            "                    likes: likeElem ? likeElem.innerText.trim() : '0'\n" +
            "                });\n" +
            "            });\n" +
            "\n" +
            "            return comments;\n" +
            "        } catch (err) {\n" +
            "            Logger.error('解析评论列表异常:', err);\n" +
            "            return [];\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    // \uD83D\uDC64 解析发布者信息\n" +
            "    function getAuthorInfo() {\n" +
            "        try {\n" +
            "            const userContainer = document.querySelector(CONFIG.SELECTORS.USER_INFO);\n" +
            "            if (!userContainer) return null;\n" +
            "\n" +
            "            const avatarImg = userContainer.querySelector('img');\n" +
            "            const statsText = userContainer.innerText || '';\n" +
            "\n" +
            "            const fansMatch = statsText.match(/粉丝\\s*([\\d\\.\\w万+]+)/);\n" +
            "            const likesMatch = statsText.match(/获赞\\s*([\\d\\.\\w万+]+)/);\n" +
            "\n" +
            "            return {\n" +
            "                name: avatarImg?.alt || userContainer.querySelector('[data-click-from=\"title\"]')?.innerText?.trim() || '未知用户',\n" +
            "                avatar: avatarImg ? avatarImg.src : '',\n" +
            "                fans: fansMatch ? fansMatch[1] : '0',\n" +
            "                likes: likesMatch ? likesMatch[1] : '0'\n" +
            "            };\n" +
            "        } catch (err) {\n" +
            "            Logger.error('获取用户信息失败:', err);\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    // \uD83D\uDCCA 解析视频指标\n" +
            "    function parseVideoMetrics() {\n" +
            "        const interactiveItems = document.querySelectorAll('.pWR86eZK .o2tLobnl');\n" +
            "        const getCount = (el) => el?.querySelector('.hIpNkUXt, .sB3y0d3B')?.innerText?.trim() || '0';\n" +
            "\n" +
            "        return {\n" +
            "            likes: interactiveItems[0] ? getCount(interactiveItems[0]) : '0',\n" +
            "            comments: interactiveItems[1] ? getCount(interactiveItems[1]) : '0',\n" +
            "            collects: interactiveItems[2] ? getCount(interactiveItems[2]) : '0',\n" +
            "            shares: interactiveItems[3] ? getCount(interactiveItems[3]) : '0',\n" +
            "            publishTime: document.querySelector('[data-e2e=\"detail-video-publish-time\"]')?.innerText?.replace('发布时间：', '').trim() || ''\n" +
            "        };\n" +
            "    }\n" +
            "\n" +
            "    // \uD83D\uDD0D 数据提取核心方法\n" +
            "    function getVideoInfo() {\n" +
            "        try {\n" +
            "            Logger.info('开始扫描视频与评论数据...');\n" +
            "\n" +
            "            const allVideos = Array.from(document.querySelectorAll('video'));\n" +
            "            const activeVideo = allVideos.find(v => !v.paused && v.offsetHeight > 0)\n" +
            "                                || allVideos.find(v => v.offsetHeight > 0)\n" +
            "                                || allVideos[0];\n" +
            "\n" +
            "            if (!activeVideo) {\n" +
            "                Logger.warn('未搜寻到可用视频节点');\n" +
            "                return null;\n" +
            "            }\n" +
            "\n" +
            "            let rawSrc = activeVideo.currentSrc || activeVideo.src || '';\n" +
            "            var sources = Array.from(activeVideo.querySelectorAll('source')).map(s => s.src).filter(Boolean);\n" +
            "            let videoUrl = rawSrc.startsWith('blob:') ? window.location.href : rawSrc;\n" +
            "\n" +
            "            let fullTitle = '';\n" +
            "            let hashtags = [];\n" +
            "            const titleElem = document.querySelector(CONFIG.SELECTORS.VIDEO_TITLE);\n" +
            "\n" +
            "            if (titleElem) {\n" +
            "                fullTitle = titleElem.innerText ? titleElem.innerText.trim().replace(/\\n/g, ' ') : '';\n" +
            "                const matchedTags = fullTitle.match(/#([^\\s#]+)/g);\n" +
            "                if (matchedTags) {\n" +
            "                    hashtags = matchedTags.map(t => t.replace('#', ''));\n" +
            "                }\n" +
            "            }\n" +
            "\n" +
            "            const data = {\n" +
            "                currentSrc: videoUrl,\n" +
            "                sources: sources,\n" +
            "                title: fullTitle || '未命名视频',\n" +
            "                hashtags: [...new Set(hashtags)],\n" +
            "                author: getAuthorInfo(),\n" +
            "                parseVideoMetrics: parseVideoMetrics(),\n" +
            "                comments: getCommentList(10)\n" +
            "            };\n" +
            "\n" +
            "            console.group('%c \uD83C\uDFAC 抖音视频数据提取成功', 'color: #165dff; font-size: 14px; font-weight: bold;');\n" +
            "            console.log('%c【视频标题】', 'font-weight: bold;', data.title);\n" +
            "            console.log('%c【地址 currentSrc】', 'font-weight: bold;', data.currentSrc);\n" +
            "            console.log('%c【源列表 sources】', 'font-weight: bold;', data.sources);\n" +
            "            console.log('%c【话题标签】', 'font-weight: bold;', data.hashtags);\n" +
            "            console.log('%c【作者信息】', 'font-weight: bold;', data.author);\n" +
            "            console.log('%c【互动指标 parseVideoMetrics】', 'font-weight: bold;', data.parseVideoMetrics);\n" +
            "\n" +
            "            if (data.comments.length > 0) {\n" +
            "                console.log('%c【评论列表 comments】', 'font-weight: bold;');\n" +
            "                console.table(data.comments);\n" +
            "            } else {\n" +
            "                console.log('%c【评论列表 comments】', 'font-weight: bold;', '暂无或未加载到评论');\n" +
            "            }\n" +
            "            console.groupEnd();\n" +
            "\n" +
            "            Logger.success('完整 JSON 对象如下：', data);\n" +
            "            return data;\n" +
            "        } catch (err) {\n" +
            "            Logger.error('数据抓取过程出现异常:', err);\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    // \uD83C\uDF10 突破沙箱隔离：安全绑定函数至原生页面 window\n" +
            "    const globalWin = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;\n" +
            "    globalWin.$getVideoInfo = getVideoInfo;\n" +
            "\n" +
            "    Logger.success('已成功挂载！直接在控制台执行 $getVideoInfo() 即可调用。', globalWin.$getVideoInfo);\n" +
            "\n" +
            "    // ⌨\uFE0F 全局快捷键监听 (防御输入区域)\n" +
            "    window.addEventListener('keydown', function (e) {\n" +
            "        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';\n" +
            "        const isInputMode = ['input', 'textarea'].includes(activeTag) || document.activeElement.isContentEditable;\n" +
            "\n" +
            "        if (isInputMode) return;\n" +
            "\n" +
            "        if (e.key.toLowerCase() === CONFIG.UI.HOT_KEY.toLowerCase()) {\n" +
            "            getVideoInfo();\n" +
            "        }\n" +
            "    });\n" +
            "\n" +
            "})();";
    /**
     *  获取视频列信息
     */
    public static  String injectJsDyVideoList = "\n" +
            "(function () {\n" +
            "    'use strict';\n" +
            "\n" +
            "    // \uD83D\uDED1 核心修复：防止脚本在 iframe 中重复执行\n" +
            "    if (window.top !== window.self) {\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    // ⚙\uFE0F 全局配置中心\n" +
            "    const CONFIG = {\n" +
            "        UI: {\n" +
            "            HOT_KEY: 'v' // 按下此键触发控制台打印\n" +
            "        },\n" +
            "        // 3. DOM 选择器映射（注：混淆类名可能随抖音更新而变动）\n" +
            "        SELECTORS: {\n" +
            "            VIDEO_LIST: '.AMqhOzPC',\n" +
            "            TITLE: '.BjLsdJMi',\n" +
            "            AUTHOR: '.WldPmwm5',\n" +
            "            TIME: '.dO8W7uoF',\n" +
            "\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    // \uD83C\uDF1F 控制台日志封装\n" +
            "    const Logger = {\n" +
            "        info: (msg, ...args) => console.log(`%c \uD83C\uDFB5 [抖音提取器] ℹ\uFE0F ${msg}`, 'color: #409EFF; font-weight: bold;', ...args),\n" +
            "        success: (msg, ...args) => console.log(`%c \uD83C\uDFB5 [抖音提取器] ✅ ${msg}`, 'color: #67C23A; font-weight: bold;', ...args),\n" +
            "        warn: (msg, ...args) => console.warn(`%c \uD83C\uDFB5 [抖音提取器] ⚠\uFE0F ${msg}`, 'color: #E6A23C; font-weight: bold;', ...args),\n" +
            "        error: (msg, ...args) => console.error(`%c \uD83C\uDFB5 [抖音提取器] ❌ ${msg}`, 'color: #F56C6C; font-weight: bold;', ...args)\n" +
            "    };\n" +
            "\n" +
            "    Logger.info(`脚本初始化启动... \uD83D\uDE80`);\n" +
            "\n" +
            "    // \uD83D\uDCAC 解析视频列表 / 点击特定视频\n" +
            "    function getVideoList(number = null, limit = 20) {\n" +
            "        try {\n" +
            "            const videoNodes = document.querySelectorAll(CONFIG.SELECTORS.VIDEO_LIST);\n" +
            "            if (!videoNodes || videoNodes.length === 0) {\n" +
            "                Logger.warn('未搜寻到视频列表节点，请检查 Selector 是否改变');\n" +
            "                return [];\n" +
            "            }\n" +
            "\n" +
            "            // 单个节点解析封装\n" +
            "            const parseNode = (node) => {\n" +
            "                const titleNode = node.querySelector(CONFIG.SELECTORS.TITLE);\n" +
            "                if (!titleNode) return null;\n" +
            "\n" +
            "                const authorNode = node.querySelector(CONFIG.SELECTORS.AUTHOR);\n" +
            "                const timeNode = node.querySelector(CONFIG.SELECTORS.TIME);\n" +
            "\n" +
            "                return {\n" +
            "                    title: titleNode ? titleNode.innerText.trim() : '未知标题',\n" +
            "                    author: authorNode ? authorNode.innerText.trim() : '未知作者',\n" +
            "                    publishTime: timeNode ? timeNode.innerText.trim() : '未知时间'\n" +
            "                };\n" +
            "            };\n" +
            "\n" +
            "            // \uD83C\uDFAF 逻辑分支 1：传入了 number，触发点击\n" +
            "            if (number !== null && number !== undefined) {\n" +
            "                const index = number - 1; // 转换为 0 基础索引\n" +
            "\n" +
            "                if (index < 0 || index >= videoNodes.length) {\n" +
            "                    Logger.warn(`索引超出范围！当前有效视频数量为 1 ~ ${videoNodes.length}`);\n" +
            "                    return null;\n" +
            "                }\n" +
            "\n" +
            "                const targetNode = videoNodes[index];\n" +
            "                const itemData = parseNode(targetNode);\n" +
            "\n" +
            "                // 优先点击内部的标题节点/链接，如果找不到则点击整个卡片节点\n" +
            "                const clickTarget = targetNode.querySelector(CONFIG.SELECTORS.TITLE) || targetNode;\n" +
            "                clickTarget.click();\n" +
            "\n" +
            "                Logger.success(`已自动点击第 ${number} 个视频:`, itemData?.title || '未知标题');\n" +
            "                // 点击后在获取视频详情\n" +
            "                return itemData;\n" +
            "            }\n" +
            "\n" +
            "            // \uD83D\uDCCB 逻辑分支 2：未传入 number，批量提取列表数据\n" +
            "            else {\n" +
            "                const videoList = [];\n" +
            "                const targetNodes = Array.from(videoNodes).slice(0, limit);\n" +
            "\n" +
            "                targetNodes.forEach(node => {\n" +
            "                    const itemData = parseNode(node);\n" +
            "                    if (itemData) videoList.push(itemData);\n" +
            "                });\n" +
            "                return videoList;\n" +
            "            }\n" +
            "\n" +
            "        } catch (err) {\n" +
            "            Logger.error('解析/点击视频列表异常:', err);\n" +
            "            return [];\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    // \uD83D\uDD0D 数据提取核心方法\n" +
            "    function consoleVideoInfo(number = null) {\n" +
            "        try {\n" +
            "            const data = {\n" +
            "                list: getVideoList(number),\n" +
            "            };\n" +
            "\n" +
            "            console.group('%c \uD83C\uDFAC 抖音视频数据提取成功', 'color: #165dff; font-size: 14px; font-weight: bold;');\n" +
            "            console.groupEnd();\n" +
            "\n" +
            "            Logger.success('完整 JSON 对象如下：', data);\n" +
            "            return data;\n" +
            "        } catch (err) {\n" +
            "            Logger.error('数据抓取过程出现异常:', err);\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "\n" +
            "    // \uD83C\uDF10 突破沙箱隔离：安全绑定函数至原生页面 window\n" +
            "    const globalWin = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;\n" +
            "    globalWin.$getVideoList = consoleVideoInfo;\n" +
            "    // ⌨\uFE0F 全局快捷键监听 (防御输入区域)\n" +
            "    window.addEventListener('keydown', function (e) {\n" +
            "        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';\n" +
            "        const isInputMode = ['input', 'textarea'].includes(activeTag) || document.activeElement.isContentEditable;\n" +
            "\n" +
            "        if (isInputMode) return;\n" +
            "\n" +
            "        if (e.key.toLowerCase() === CONFIG.UI.HOT_KEY.toLowerCase()) {\n" +
            "            consoleVideoInfo();\n" +
            "        }\n" +
            "    });\n" +
            "\n" +
            "})();";
}
