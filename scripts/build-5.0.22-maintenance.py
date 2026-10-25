"""Build the complete 5.0.22 from the verified, original-signed 5.0.21."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location(
        'maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = 'c88202a1dc42c6d835868875cc42a6985bb23fa351d5c87c251d4ec742cd5018'
    builder.VERSION_NAME = '5.0.22'
    builder.VERSION_CODE = 81
    builder.BUILD_DIR = 'maintenance522'
    builder.__doc__ = __doc__
    builder.main()
