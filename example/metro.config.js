const path = require('path')
const { getDefaultConfig } = require('expo/metro-config')

const projectRoot = __dirname
const libraryRoot = path.resolve(projectRoot, '..')

const config = getDefaultConfig(projectRoot)

config.watchFolders = [libraryRoot]

config.resolver.nodeModulesPaths = [
    path.resolve(projectRoot, 'node_modules')
]

config.resolver.extraNodeModules = {
    ...config.resolver.extraNodeModules,
    'whatwg-url-minimum': path.resolve(projectRoot, 'node_modules/whatwg-url-minimum')
}

config.resolver.disableHierarchicalLookup = true

config.resolver.unstable_conditionNames = [
    'source',
    'import',
    'require',
    'default'
]

const standardResolve = config.resolver.resolveRequest

config.resolver.resolveRequest = (context, moduleName, platform) => {
    if (
        moduleName === 'react' ||
        moduleName.startsWith('react/') ||
        moduleName === 'react-native' ||
        moduleName.startsWith('react-native/') ||
        moduleName.startsWith('@expo/')
    ) {
        return context.resolveRequest(
            {
                ...context,
                originModulePath: path.resolve(projectRoot, 'index.ts')
            },
            moduleName,
            platform
        )
    }

    if (standardResolve) {
        return standardResolve(context, moduleName, platform)
    }

    return context.resolveRequest(context, moduleName, platform)
}

module.exports = config