"""Build the complete 5.0.20 from the verified, original-signed 5.0.19."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location(
        'maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = 'aab6674874299c1cb772c365f46b142d5d861370574b7d15fd04919c09cbb640'
    builder.VERSION_NAME = '5.0.20'
    builder.VERSION_CODE = 79
    builder.BUILD_DIR = 'maintenance520'
    builder.__doc__ = __doc__
    builder.main()
