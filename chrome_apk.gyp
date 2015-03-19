# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
{
  'includes': [
    '../../chrome/chrome_android_paks.gypi', # Included for the list of pak resources.
    '../../build/util/version.gypi',
    '../../components/web_refiner/web_refiner_vars.gypi',
    '../../third_party/libsweadrenoext/libsweadrenoext_vars.gypi',
   ],
  'variables': {
    'chromium_code': 1,
    'package_name': 'swe_browser_apk',
    #TODO:This value needs to be updated from swe channel info
    'swe_branding_file' : '<!pymod_do_main(swe_channels_dirs --swe-channels <(swe_channels) \
                           -d <(DEPTH) --branding-folder)/BRANDING',
    'manifest_package': '<!(python <(DEPTH)/swe/tools/swe_config.py \
                                        -i <(swe_branding_file) \
                                        -c PACKAGE_NAME)',

    'swe_app_manifest_version_code': '<!(python <(DEPTH)/swe/tools/swe_version.py \
                                        -i <(DEPTH)/chrome/VERSION \
                                        -o <(DEPTH)/swe/VERSION \
                                        --version-code-only)',

    'swe_app_manifest_version_name': '<!(python <(DEPTH)/swe/tools/swe_version.py \
                                        -i <(DEPTH)/chrome/VERSION \
                                        -o <(DEPTH)/swe/VERSION \
                                        --version-string-only)',
    'conditions': [
      ['target_arch=="arm64"', {
        'architecture_name':'x64'
      }, {
        'architecture_name':'x32'
      }],
      ['clang==0', {
        'compiler_name':'gcc'
      }, {
        'compiler_name':'llvm'
      }],
    ],
    'chrome_public_apk_manifest': '<(SHARED_INTERMEDIATE_DIR)/swe_browser_apk_manifest/AndroidManifest.xml',
    'chrome_public_test_apk_manifest': '<(SHARED_INTERMEDIATE_DIR)/swe_browser_test_apk_manifest/AndroidManifest.xml',
    # This list is shared with GN.
    # Defines a list of source files should be present in the open-source
    # chrome-apk but not in the published static_library which is included in the
    # real chrome for android.
    'chrome_public_app_native_sources': [
      '../browser/android/chrome_entry_point.cc',
      '../browser/android/chrome_main_delegate_staging_android_initializer.cc',
    ],
  },
  'targets': [
    {
      #GN: //chrome/android::custom_tabs_service_aidl
      'target_name': 'custom_tabs_service_aidl',
      'type': 'none',
      'variables': {
        'aidl_interface_file': 'java/src/android/support/customtabs/common.aidl',
        'aidl_import_include': 'java/src/android/support/customtabs',
      },
      'sources': [
        'java/src/android/support/customtabs/ICustomTabsCallback.aidl',
        'java/src/android/support/customtabs/ICustomTabsService.aidl',
      ],
      'includes': [ '../../build/java_aidl.gypi' ],
    },
    {
      # GN: //chrome/android:chrome_public_template_resources
      'target_name': 'chrome_public_template_resources',
      'type': 'none',
      'variables': {
        'jinja_inputs_base_dir': 'java/res_template',
        'jinja_inputs': [
          '<(jinja_inputs_base_dir)/xml/searchable.xml',
          '<(jinja_inputs_base_dir)/xml/syncadapter.xml',
        ],
        'jinja_outputs_zip': '<(PRODUCT_DIR)/res.java/<(_target_name).zip',
        'jinja_variables': [
          'manifest_package=<(manifest_package)',
        ],
      },
      'all_dependent_settings': {
        'variables': {
          'additional_input_paths': ['<(jinja_outputs_zip)'],
          'dependencies_res_zip_paths': ['<(jinja_outputs_zip)'],
        },
      },
      'includes': [ '../../build/android/jinja_template.gypi' ],
    },
    {
      # GN: //chrome/android:chrome_public
      'target_name': 'libswe',
      'type': 'shared_library',
      'dependencies': [
        '../../chrome/chrome.gyp:chrome_android_core',
      ],
      'include_dirs': [
        '../..',
      ],
      'sources': [
        '<@(chrome_public_app_native_sources)',
      ],
      'ldflags': [
        # Some android targets still depend on --gc-sections to link.
        # TODO: remove --gc-sections for Debug builds (crbug.com/159847).
        '-Wl,--gc-sections',
      ],
      'conditions': [
        # TODO(yfriedman): move this DEP to chrome_android_core to be shared
        # between internal/external.
        ['cld_version==2', {
          'dependencies': [
            '../../third_party/cld_2/cld_2.gyp:cld2_dynamic',
          ],
        }],
        # conditions for order_text_section
        # Cygprofile methods need to be linked into the instrumented build.
        ['order_profiling!=0', {
          'conditions': [
            ['OS=="android"', {
              'dependencies': [ '../../tools/cygprofile/cygprofile.gyp:cygprofile' ],
            }],
          ],
        }],  # order_profiling!=0
        ['use_allocator!="none"', {
          'dependencies': [
            '../../base/allocator/allocator.gyp:allocator',
          ],
        }],
      ],
    },
    {
      # GN: //chrome/android:chrome_public_apk_manifest
      'target_name': 'chrome_public_manifest',
      'type': 'none',
      'variables': {
        'jinja_inputs': ['java/AndroidManifest.xml'],
        'jinja_output': '<(chrome_public_apk_manifest)',
        'jinja_variables': [
          'channel=<(android_channel)',
          'configuration_policy=<(configuration_policy)',
          'manifest_package=<(manifest_package)',
          'min_sdk_version=16',
          'target_sdk_version=23',
          'apk_compiler=<(compiler_name)',
          'apk_architecture=<(architecture_name)',
        ],
      },
      'includes': [ '../../build/android/jinja_template.gypi' ],
    },
    {
      # GN: //chrome/android:chrome_public_apk
      'target_name': 'swe_browser_apk',
      'type': 'none',
      'variables': {
        'android_manifest_path': '<(chrome_public_apk_manifest)',
        'apk_name': 'SWE_Browser',
        'native_lib_target': 'libswe',
        'app_manifest_version_code': '<(swe_app_manifest_version_code)',
        'app_manifest_version_name': '<(swe_app_manifest_version_name)',
        'java_in_dir': 'java',
        'resource_dir': '../../chrome/android/java/res_chromium',
        'extra_native_libs': [
          '<@(web_refiner_native_libs)',
          '<@(libsweadrenoext_native_libs)',
        ],
        'res_extra_dirs': ['<!@pymod_do_main(swe_channels_dirs --swe-channels <(swe_channels) \
                           -d <(DEPTH) --channel-res-folder)',],
        'conditions': [
          # Only attempt loading the library from the APK for 64 bit devices
          # until the number of 32 bit devices which don't support this
          # approach falls to a minimal level -  http://crbug.com/390618.
          ['component != "shared_library" and profiling==0 and (target_arch == "arm64" or target_arch == "x86_64")', {
            'load_library_from_zip_file': '<(chrome_apk_load_library_from_zip)',
            'load_library_from_zip': '<(chrome_apk_load_library_from_zip)',
          }],
        ],
      },
      'dependencies': [
        'chrome_android_paks_copy',
        'chrome_public_template_resources',
        '../chrome.gyp:chrome_java',
        '<@(web_refiner_dependencies)',
        '../<@(libsweadrenoext_dependencies)',
      ],
      'includes': [ 'chrome_apk.gypi' ],
    },
    {
      # GN: N/A
      # chrome_public_apk creates a .jar as a side effect. Any java targets
      # that need that .jar in their classpath should depend on this target,
      'target_name': 'swe_browser_apk_java',
      'type': 'none',
      'dependencies': [
        'swe_browser_apk',
      ],
      'includes': [ '../../build/apk_fake_jar.gypi' ],
    },
    {
      # GN: //chrome/android:chrome_shared_test_java
      # This target is for sharing tests between both upstream and internal
      # trees until sufficient test coverage is upstream.
      'target_name': 'chrome_shared_test_java',
      'type': 'none',
      'variables': {
        'java_in_dir': 'javatests',
      },
      'dependencies': [
        '../../base/base.gyp:base_java',
        '../../base/base.gyp:base_java_test_support',
        '../../chrome/chrome.gyp:chrome_java',
        '../../chrome/chrome.gyp:chrome_java_test_support',
        '../../components/components.gyp:invalidation_javatests',
        '../../components/components.gyp:offline_pages_enums_java',
        '../../components/components.gyp:precache_javatests',
        '../../components/components.gyp:web_contents_delegate_android_java',
        '../../content/content_shell_and_tests.gyp:content_java_test_support',
        '../../net/net.gyp:net_java',
        '../../net/net.gyp:net_java_test_support',
        '../../sync/sync.gyp:sync_java_test_support',
        '../../sync/sync.gyp:sync_javatests',
        '../../third_party/android_tools/android_tools.gyp:android_support_v7_appcompat_javalib',
        '../../ui/android/ui_android.gyp:ui_javatests',
      ],
      'includes': [ '../../build/java.gypi' ],
    },
    {
      # GN: //chrome/android:chrome_public_test_apk_manifest
      'target_name': 'swe_browser_test_apk_manifest',
      'type': 'none',
      'variables': {
        'jinja_inputs': ['javatests/AndroidManifest.xml'],
        'jinja_output': '<(chrome_public_test_apk_manifest)',
        'jinja_variables': [
          'manifest_package=<(manifest_package)',
        ],
      },
      'includes': [ '../../build/android/jinja_template.gypi' ],
    },
    {
      # GN: //chrome/android:chrome_public_test_apk
      'target_name': 'swe_browser_test_apk',
      'type': 'none',
      'dependencies': [
        'chrome_shared_test_java',
        'swe_browser_apk_java',
        '../../testing/android/on_device_instrumentation.gyp:broker_java',
        '../../testing/android/on_device_instrumentation.gyp:require_driver_apk',
      ],
      'variables': {
        'android_manifest_path': '<(chrome_public_test_apk_manifest)',
        'package_name': 'swe_browser_test',
        'java_in_dir': 'javatests',
        'java_in_dir_suffix': '/src_dummy',
        'apk_name': 'SWE_BrowserTest',
        'is_test_apk': 1,
        'test_type': 'instrumentation',
        'isolate_file': '../chrome_public_test_apk.isolate',
      },
      'includes': [
        '../../build/java_apk.gypi',
        '../../build/android/test_runner.gypi',
      ],
    },
  ],
}

# Local Variables:
# tab-width:2
# indent-tabs-mode:nil
# End:
# vim: set expandtab tabstop=2 shiftwidth=2:
