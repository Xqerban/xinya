import * as quansync_macro from 'quansync/macro';
import { PackageJson } from 'pkg-types';

interface PackageInfo {
    name: string;
    rootPath: string;
    packageJsonPath: string;
    version: string;
    packageJson: PackageJson;
}
interface PackageResolvingOptions {
    paths?: string[];
    /**
     * @default 'auto'
     * Resolve path as posix or win32
     */
    platform?: 'posix' | 'win32' | 'auto';
}
declare function resolveModule(name: string, options?: PackageResolvingOptions): any;
declare function importModule<T = any>(path: string): Promise<T>;
declare function isPackageExists(name: string, options?: PackageResolvingOptions): boolean;
declare const getPackageInfo: quansync_macro.QuansyncFn<{
    name: string;
    version: string | undefined;
    rootPath: any;
    packageJsonPath: any;
    packageJson: PackageJson;
} | undefined, [name: string, options?: PackageResolvingOptions | undefined]>;
declare const getPackageInfoSync: (name: string, options?: PackageResolvingOptions | undefined) => {
    name: string;
    version: string | undefined;
    rootPath: any;
    packageJsonPath: any;
    packageJson: PackageJson;
} | undefined;
declare const loadPackageJSON: quansync_macro.QuansyncFn<PackageJson | null, [cwd?: Args[0] | undefined]>;
declare const loadPackageJSONSync: (cwd?: Args[0] | undefined) => PackageJson | null;
declare const isPackageListed: quansync_macro.QuansyncFn<boolean, [name: string, cwd?: string | undefined]>;
declare const isPackageListedSync: (name: string, cwd?: string | undefined) => boolean;

export { getPackageInfo, getPackageInfoSync, importModule, isPackageExists, isPackageListed, isPackageListedSync, loadPackageJSON, loadPackageJSONSync, resolveModule };
export type { PackageInfo, PackageResolvingOptions };
