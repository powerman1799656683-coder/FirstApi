import i18n from './i18n';
import hardcodedEn from './locales/hardcoded-en.json';

const TRANSLATABLE_ATTRIBUTES = ['title', 'placeholder', 'aria-label', 'alt'];
const textOrigins = new WeakMap();
const attributeOrigins = new WeakMap();

let installed = false;
let observer = null;
let languageHandler = null;
let isApplying = false;
let scheduled = false;
const pendingRoots = new Set();

function hasCjk(value) {
    return /[\u3400-\u9fff]/.test(value);
}

function isEnglishLanguage() {
    const language = (i18n.resolvedLanguage || i18n.language || '').toLowerCase();
    return language.startsWith('en');
}

function translateText(text) {
    if (!isEnglishLanguage()) {
        return text;
    }

    if (typeof text !== 'string' || !hasCjk(text)) {
        return text;
    }

    const directMatch = hardcodedEn[text];
    if (directMatch) {
        return directMatch;
    }

    const trimmed = text.trim();
    const trimmedMatch = hardcodedEn[trimmed];
    if (!trimmedMatch) {
        return text;
    }

    if (trimmed === text) {
        return trimmedMatch;
    }

    return text.replace(trimmed, trimmedMatch);
}

function getElementOriginMap(element) {
    let origins = attributeOrigins.get(element);
    if (!origins) {
        origins = new Map();
        attributeOrigins.set(element, origins);
    }
    return origins;
}

function processTextNode(textNode, englishMode) {
    const currentText = textNode.textContent || '';
    if (!currentText) {
        return;
    }

    if (englishMode) {
        if (hasCjk(currentText)) {
            textOrigins.set(textNode, currentText);
        }

        const originalText = textOrigins.get(textNode);
        if (!originalText) {
            return;
        }

        const translated = translateText(originalText);
        if (translated === currentText) {
            return;
        }

        if (!hasCjk(currentText) && currentText !== translateText(originalText)) {
            textOrigins.delete(textNode);
            return;
        }

        textNode.textContent = translated;
        return;
    }

    if (hasCjk(currentText)) {
        textOrigins.set(textNode, currentText);
        return;
    }

    const originalText = textOrigins.get(textNode);
    if (!originalText || originalText === currentText) {
        return;
    }

    textNode.textContent = originalText;
}

function processElementAttributes(element, englishMode) {
    const origins = getElementOriginMap(element);

    for (const attribute of TRANSLATABLE_ATTRIBUTES) {
        const currentValue = element.getAttribute(attribute);
        if (!currentValue) {
            continue;
        }

        if (englishMode) {
            if (hasCjk(currentValue)) {
                origins.set(attribute, currentValue);
            }

            const originalValue = origins.get(attribute);
            if (!originalValue) {
                continue;
            }

            const translated = translateText(originalValue);
            if (translated === currentValue) {
                continue;
            }

            if (!hasCjk(currentValue) && currentValue !== translateText(originalValue)) {
                origins.delete(attribute);
                continue;
            }

            element.setAttribute(attribute, translated);
            continue;
        }

        if (hasCjk(currentValue)) {
            origins.set(attribute, currentValue);
            continue;
        }

        const originalValue = origins.get(attribute);
        if (!originalValue || originalValue === currentValue) {
            continue;
        }

        element.setAttribute(attribute, originalValue);
    }
}

function processNode(node, englishMode) {
    if (!node) {
        return;
    }

    if (node.nodeType === Node.TEXT_NODE) {
        processTextNode(node, englishMode);
        return;
    }

    if (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) {
        return;
    }

    if (node.nodeType === Node.ELEMENT_NODE) {
        const element = node;
        const tagName = element.tagName?.toLowerCase();
        if (tagName === 'script' || tagName === 'style') {
            return;
        }

        processElementAttributes(element, englishMode);
    }

    for (const child of node.childNodes) {
        processNode(child, englishMode);
    }
}

function flush() {
    scheduled = false;
    if (isApplying) {
        return;
    }

    const roots = pendingRoots.size > 0 ? [...pendingRoots] : [document.getElementById('root') || document.body];
    pendingRoots.clear();

    isApplying = true;
    const englishMode = isEnglishLanguage();
    try {
        for (const root of roots) {
            processNode(root, englishMode);
        }
    } finally {
        isApplying = false;
    }
}

function schedule(root) {
    if (!root) {
        return;
    }

    pendingRoots.add(root);
    if (scheduled) {
        return;
    }

    scheduled = true;
    Promise.resolve().then(flush);
}

export function installHardcodedTranslation() {
    if (installed || typeof window === 'undefined' || typeof document === 'undefined') {
        return;
    }

    installed = true;
    const appRoot = document.getElementById('root') || document.body;

    languageHandler = () => {
        schedule(appRoot);
    };
    i18n.on('languageChanged', languageHandler);

    observer = new MutationObserver((mutations) => {
        if (isApplying) {
            return;
        }

        for (const mutation of mutations) {
            if (mutation.type === 'characterData') {
                schedule(mutation.target);
                continue;
            }

            if (mutation.type === 'attributes') {
                schedule(mutation.target);
                continue;
            }

            if (mutation.type === 'childList') {
                for (const addedNode of mutation.addedNodes) {
                    schedule(addedNode);
                }
            }
        }
    });

    observer.observe(appRoot, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true,
        attributeFilter: TRANSLATABLE_ATTRIBUTES,
    });

    schedule(appRoot);
}

