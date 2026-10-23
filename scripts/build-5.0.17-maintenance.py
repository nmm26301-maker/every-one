"""Build the complete 5.0.17 from the verified, original-signed 5.0.16."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location(
        'maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = '1dd2a0bb0599707bdd352885f11b9405c5f02640f2fee23b0b30d53a8320b7b2'
    builder.VERSION_NAME = '5.0.17'
    builder.VERSION_CODE = 76
    builder.BUILD_DIR = 'maintenance517'
    builder.__doc__ = __doc__
    builder.main()
