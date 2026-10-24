"""Build the complete 5.0.19 from the verified, original-signed 5.0.18."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location(
        'maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = '3081ab95d37decd17cce31f65d131fdf854018a8b25f1d9226c10af21ee19145'
    builder.VERSION_NAME = '5.0.19'
    builder.VERSION_CODE = 78
    builder.BUILD_DIR = 'maintenance519'
    builder.__doc__ = __doc__
    builder.main()
