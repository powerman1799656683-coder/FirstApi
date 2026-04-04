import React, { useState, useRef, useEffect } from 'react';
import ReactDOM from 'react-dom';
import { ChevronDown, Check } from 'lucide-react';

const Select = ({ value, onChange, children, className, placeholder, style, disabled, dropUp }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [focusedIndex, setFocusedIndex] = useState(-1);
    const containerRef = useRef(null);
    const triggerRef = useRef(null);
    const [dropStyle, setDropStyle] = useState(null);

    // Extract options from children
    const options = React.Children.map(children, child => {
        if (!child) return null;
        return {
            label: child.props.children,
            value: child.props.value !== undefined ? child.props.value : child.props.children,
            disabled: child.props.disabled
        };
    }).filter(Boolean);

    const toggleOpen = () => {
        if (disabled) return;
        const willOpen = !isOpen;
        setIsOpen(willOpen);
        if (willOpen) {
            const idx = options.findIndex(opt => opt.value === value);
            setFocusedIndex(idx >= 0 ? idx : 0);
            if (dropUp && triggerRef.current) {
                const rect = triggerRef.current.getBoundingClientRect();
                setDropStyle({
                    position: 'fixed',
                    left: rect.left,
                    bottom: window.innerHeight - rect.top + 8,
                    minWidth: rect.width,
                    zIndex: 9999,
                });
            }
        }
    };

    const portalRef = useRef(null);

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (containerRef.current && !containerRef.current.contains(event.target) &&
                (!portalRef.current || !portalRef.current.contains(event.target))) {
                setIsOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleSelect = (optValue, optDisabled) => {
        if (optDisabled) return;
        onChange({ target: { value: optValue } });
        setIsOpen(false);
    };

    const handleKeyDown = (e) => {
        if (disabled) return;

        switch (e.key) {
            case 'Enter':
            case ' ':
                e.preventDefault();
                if (isOpen && focusedIndex >= 0 && !options[focusedIndex]?.disabled) {
                    handleSelect(options[focusedIndex].value, false);
                } else {
                    toggleOpen();
                }
                break;
            case 'ArrowDown':
                e.preventDefault();
                if (!isOpen) {
                    setIsOpen(true);
                    const idx = options.findIndex(opt => opt.value === value);
                    setFocusedIndex(idx >= 0 ? idx : 0);
                } else {
                    setFocusedIndex(prev => {
                        let next = prev + 1;
                        while (next < options.length && options[next]?.disabled) next++;
                        return next < options.length ? next : prev;
                    });
                }
                break;
            case 'ArrowUp':
                e.preventDefault();
                if (isOpen) {
                    setFocusedIndex(prev => {
                        let next = prev - 1;
                        while (next >= 0 && options[next]?.disabled) next--;
                        return next >= 0 ? next : prev;
                    });
                }
                break;
            case 'Escape':
                e.preventDefault();
                setIsOpen(false);
                break;
            default:
                break;
        }
    };

    const selectedOption = options.find(opt => opt.value === value);

    const displayLabel = selectedOption
        ? selectedOption.label
        : (placeholder || (options[0] ? options[0].label : ''));

    const triggerClassName = ['custom-select-trigger', className, isOpen ? 'active' : '']
        .filter(Boolean)
        .join(' ');

    return (
        <div ref={containerRef} className="custom-select-container" style={style}>
            <div
                ref={triggerRef}
                className={triggerClassName}
                onClick={toggleOpen}
                onKeyDown={handleKeyDown}
                tabIndex={disabled ? -1 : 0}
                role="combobox"
                aria-expanded={isOpen}
                aria-haspopup="listbox"
                aria-disabled={disabled || undefined}
            >
                <span className="select-value-text">{displayLabel}</span>
                <ChevronDown size={14} className={`select-arrow ${isOpen ? 'open' : ''}`} style={{ opacity: 0.6 }} />
            </div>

            {isOpen && !dropUp && (
                <div className="custom-select-options" role="listbox">
                    {options.map((opt, i) => (
                        <div
                            key={i}
                            className={`custom-option ${opt.value === value ? 'selected' : ''} ${opt.disabled ? 'disabled' : ''} ${i === focusedIndex ? 'focused' : ''}`}
                            onClick={() => handleSelect(opt.value, opt.disabled)}
                            role="option"
                            aria-selected={opt.value === value}
                            aria-disabled={opt.disabled || undefined}
                        >
                            <span>{opt.label}</span>
                            {opt.value === value && <Check size={14} className="selected-check" />}
                        </div>
                    ))}
                </div>
            )}

            {isOpen && dropUp && ReactDOM.createPortal(
                <div ref={portalRef} className="custom-select-options custom-select-options--up" role="listbox" style={dropStyle || {}}>
                    {options.map((opt, i) => (
                        <div
                            key={i}
                            className={`custom-option ${opt.value === value ? 'selected' : ''} ${opt.disabled ? 'disabled' : ''} ${i === focusedIndex ? 'focused' : ''}`}
                            onClick={() => handleSelect(opt.value, opt.disabled)}
                            role="option"
                            aria-selected={opt.value === value}
                            aria-disabled={opt.disabled || undefined}
                        >
                            <span>{opt.label}</span>
                            {opt.value === value && <Check size={14} className="selected-check" />}
                        </div>
                    ))}
                </div>,
                document.body
            )}
        </div>
    );
};

export default Select;
