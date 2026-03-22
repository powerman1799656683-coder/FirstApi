import React, { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, Check } from 'lucide-react';

const LanguageSwitcher = () => {
    const { i18n } = useTranslation();
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef(null);
    const menuId = 'language-switcher-menu';

    const languages = [
        { code: 'zh', label: '简体中文', short: 'ZH' },
        { code: 'en', label: '英文', short: 'EN' }
    ];

    const normalizedLanguage = (i18n.resolvedLanguage || i18n.language || 'zh').toLowerCase().split(/[-_]/)[0];
    const currentLang = languages.find((lang) => lang.code === normalizedLanguage) || languages[0];

    const toggleOpen = () => setIsOpen((open) => !open);

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (containerRef.current && !containerRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        };

        const handleEscape = (event) => {
            if (event.key === 'Escape') {
                setIsOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        document.addEventListener('keydown', handleEscape);

        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('keydown', handleEscape);
        };
    }, []);

    const handleLanguageChange = (code) => {
        i18n.changeLanguage(code);
        setIsOpen(false);
    };

    return (
        <div ref={containerRef} className="language-switcher-container">
            <button
                type="button"
                className="language-switcher-trigger"
                onClick={toggleOpen}
                aria-haspopup="menu"
                aria-expanded={isOpen}
                aria-controls={menuId}
                aria-label="切换语言"
            >
                <span style={{ opacity: 0.8 }}>{currentLang.short}</span>
                <ChevronDown
                    size={14}
                    style={{
                        transition: 'transform 0.3s',
                        transform: isOpen ? 'rotate(180deg)' : 'none',
                        opacity: 0.6
                    }}
                />
            </button>

            {isOpen && (
                <div id={menuId} className="language-switcher-menu" role="menu">
                    {languages.map((lang) => (
                        <button
                            key={lang.code}
                            type="button"
                            role="menuitemradio"
                            aria-checked={normalizedLanguage === lang.code}
                            className={`language-switcher-option ${normalizedLanguage === lang.code ? 'active' : ''}`}
                            onClick={() => handleLanguageChange(lang.code)}
                        >
                            <span>{lang.label}</span>
                            {normalizedLanguage === lang.code && <Check size={14} style={{ color: 'var(--primary-tech)' }} />}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

export default LanguageSwitcher;
