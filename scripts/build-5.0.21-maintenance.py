"""Build the complete 5.0.21 from the verified, original-signed 5.0.20."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location(
        'maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = 'aa993420817037e80c512ca06738e2c55273a8bf5434cd18c628bb50a3077f27'
    builder.VERSION_NAME = '5.0.21'
    builder.VERSION_CODE = 80
    builder.BUILD_DIR = 'maintenance521'
    builder.__doc__ = __doc__
    builder.main()
