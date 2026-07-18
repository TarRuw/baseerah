package com.baseerah.shared;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Locale-aware resolver for the API's server-provided <em>content</em> strings (Step 8.1, QA finding
 * I18N-01/COPY-01): loan verdicts, grounded chat replies, rescue options, and challenge copy. Every one is
 * resolved from {@code messages*.properties} for the caller's request locale, so an {@code Accept-Language: ar}
 * request receives Arabic and {@code en} (or anything else) receives the default English bundle — closing the
 * gap where the backend leaked English into the Arabic-first UI.
 *
 * <p>Two resolution modes, one code path:
 * <ul>
 *   <li>{@link #get(String, Object...)} — resolves for the <strong>current request locale</strong> via
 *       {@link LocaleContextHolder}, which Spring populates from {@code Accept-Language} on the request thread.
 *       Used by services that do not already carry the {@link Locale} (loan, rescue, chat).</li>
 *   <li>{@link #get(Locale, String, Object...)} — resolves for an <strong>explicit</strong> locale. Used where
 *       the controller already threads {@code Accept-Language} into the service as a {@link Locale} (the
 *       challenge read path).</li>
 * </ul>
 *
 * <p>Backed by Spring's auto-configured {@link MessageSource} ({@code spring.messages.*}); the parameterised
 * overloads run {@link java.text.MessageFormat} for {@code {0}}-style substitution. Numeric arguments are
 * passed pre-formatted (Western digits) so grouping never shifts between locales.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Resolve {@code key} (with optional {@code args}) for the current request locale ({@code Accept-Language}). */
    public String get(String key, Object... args) {
        return get(LocaleContextHolder.getLocale(), key, args);
    }

    /** Resolve {@code key} (with optional {@code args}) for an explicit {@code locale}. */
    public String get(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale == null ? Locale.ENGLISH : locale);
    }

    /**
     * A standalone {@link Messages} backed directly by the {@code messages*.properties} bundles, for pure unit
     * tests that construct a service without a Spring context (the same bundles the app loads at runtime).
     */
    public static Messages forTests() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return new Messages(source);
    }
}
