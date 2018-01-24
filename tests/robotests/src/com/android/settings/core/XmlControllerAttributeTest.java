package com.android.settings.core;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.provider.SearchIndexableResource;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Xml;

import com.android.settings.R;
import com.android.settings.TestConfig;
import com.android.settings.search.DatabaseIndexingUtils;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchFeatureProvider;
import com.android.settings.search.SearchFeatureProviderImpl;
import com.android.settings.search.XmlParserUtils;
import com.android.settings.security.SecuritySettings;
import com.android.settings.security.SecuritySettingsV2;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settings.testutils.SettingsRobolectricTestRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class XmlControllerAttributeTest {

    // List of classes that are too hard to mock in order to retrieve xml information.
    private final List<Class> illegalClasses = new ArrayList<>(
            Arrays.asList(
                    SecuritySettings.class,
                    SecuritySettingsV2.class
            ));

    // List of XML that could be retrieved from the illegalClasses list.
    private final List<Integer> whitelistXml = new ArrayList<>(
            Arrays.asList(
                    R.xml.security_settings_misc,
                    R.xml.security_settings_lockscreen_profile,
                    R.xml.security_settings_lockscreen,
                    R.xml.security_settings_chooser,
                    R.xml.security_settings_pattern_profile,
                    R.xml.security_settings_pin_profile,
                    R.xml.security_settings_password_profile,
                    R.xml.security_settings_pattern,
                    R.xml.security_settings_pin,
                    R.xml.security_settings_password,
                    R.xml.security_settings,
                    R.xml.security_settings_status
            ));

    private static final String NO_VALID_CONSTRUCTOR_ERROR =
            "Controllers added in XML need a constructor following either:"
                    + "\n\tClassName(Context)\n\tClassName(Context, String)"
                    + "\nThese controllers are missing a valid constructor:\n";

    private static final String NOT_BASE_PREF_CONTROLLER_ERROR =
            "Controllers added in XML need to extend com.android.settings.core"
                    + ".BasePreferenceController\nThese controllers do not:\n";

    private static final String BAD_CLASSNAME_ERROR =
            "The following controllers set in the XML did not have valid class names:\n";

    Context mContext;
    SearchFeatureProvider mSearchProvider;
    private FakeFeatureFactory mFakeFeatureFactory;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mSearchProvider = new SearchFeatureProviderImpl();
        mFakeFeatureFactory = FakeFeatureFactory.setupForTest();
        mFakeFeatureFactory.searchFeatureProvider = mSearchProvider;
    }

    @After
    public void cleanUp() {
        mFakeFeatureFactory.searchFeatureProvider = mock(
                SearchFeatureProvider.class);
    }

    @Test
    public void testAllIndexableXML_onlyValidBasePreferenceControllersAdded() {
        Set<Integer> xmlSet = getIndexableXml();
        xmlSet.addAll(whitelistXml);

        List<String> xmlControllers = new ArrayList<>();
        Set<String> invalidConstructors = new HashSet<>();
        Set<String> invalidClassHierarchy = new HashSet<>();
        Set<String> badClassNameControllers = new HashSet<>();

        for (int resId : xmlSet) {
            xmlControllers.addAll(getXmlControllers(resId));
        }

        for (String controllerClassName : xmlControllers) {
            Class<?> clazz = getClassFromClassName(controllerClassName);

            if (clazz == null) {
                badClassNameControllers.add(controllerClassName);
                continue;
            }

            Constructor<?> constructor = getConstructorFromClass(clazz);

            if (constructor == null) {
                invalidConstructors.add(controllerClassName);
                continue;
            }

            if (!isBasePreferenceController(clazz)) {
                invalidClassHierarchy.add(controllerClassName);
            }
        }

        final String invalidConstructorError = buildErrorMessage(NO_VALID_CONSTRUCTOR_ERROR,
                invalidConstructors);
        final String invalidClassHierarchyError = buildErrorMessage(NOT_BASE_PREF_CONTROLLER_ERROR,
                invalidClassHierarchy);
        final String badClassNameError = buildErrorMessage(BAD_CLASSNAME_ERROR,
                badClassNameControllers);

        assertWithMessage(invalidConstructorError).that(invalidConstructors).isEmpty();
        assertWithMessage(invalidClassHierarchyError).that(invalidClassHierarchy).isEmpty();
        assertWithMessage(badClassNameError).that(badClassNameControllers).isEmpty();
    }

    private Set<Integer> getIndexableXml() {
        Set<Integer> xmlResSet = new HashSet();

        Collection<Class> indexableClasses =
                mSearchProvider.getSearchIndexableResources().getProviderValues();
        indexableClasses.removeAll(illegalClasses);

        for (Class clazz : indexableClasses) {

            Indexable.SearchIndexProvider provider = DatabaseIndexingUtils.getSearchIndexProvider(
                    clazz);

            if (provider == null) {
                continue;
            }

            List<SearchIndexableResource> resources = provider.getXmlResourcesToIndex(mContext,
                    true);

            if (resources == null) {
                continue;
            }

            for (SearchIndexableResource resource : resources) {
                // Add '0's anyway. It won't break the test.
                xmlResSet.add(resource.xmlResId);
            }
        }
        return xmlResSet;
    }

    private List<String> getXmlControllers(int xmlResId) {
        List<String> xmlControllers = new ArrayList<>();

        XmlResourceParser parser;
        try {
            parser = mContext.getResources().getXml(xmlResId);

            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
                // Parse next until start tag is found
            }

            final int outerDepth = parser.getDepth();
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            String controllerClassName;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                controllerClassName = XmlParserUtils.getController(mContext, attrs);
                // If controller is not indexed, then it is not compatible with
                if (!TextUtils.isEmpty(controllerClassName)) {
                    xmlControllers.add(controllerClassName);
                }
            }
        } catch (Exception e) {
            // Assume an issue with robolectric resources
        }
        return xmlControllers;
    }

    private String buildErrorMessage(String errorSummary, Set<String> errorClasses) {
        final StringBuilder error = new StringBuilder(errorSummary);
        for (String c : errorClasses) {
            error.append(c).append("\n");
        }
        return error.toString();
    }

    private Class<?> getClassFromClassName(String className) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
        }
        return clazz;
    }

    private Constructor<?> getConstructorFromClass(Class<?> clazz) {
        Constructor<?> constructor = null;
        try {
            constructor = clazz.getConstructor(Context.class);
        } catch (NoSuchMethodException e) {
        }

        if (constructor != null) {
            return constructor;
        }

        try {
            constructor = clazz.getConstructor(Context.class, String.class);
        } catch (NoSuchMethodException e) {
        }

        return constructor;
    }

    /**
     * Make sure that {@link BasePreferenceController} is in the class hierarchy.
     */
    private boolean isBasePreferenceController(Class<?> clazz) {
        while (clazz != null) {
            clazz = clazz.getSuperclass();
            if (BasePreferenceController.class.equals(clazz)) {
                return true;
            }
        }
        return false;
    }
}