"""Build the complete 5.0.18 from the verified, original-signed 5.0.17."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location(
        'maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = '3da2ca02b303a1448b27bfc538b0c3b071e1c9045c9e44a8f7164c6253c7b60a'
    builder.VERSION_NAME = '5.0.18'
    builder.VERSION_CODE = 77
    builder.BUILD_DIR = 'maintenance518'
    builder.__doc__ = __doc__
    builder.main()
