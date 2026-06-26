const isNil = (o)=>null == o;
const isFunction = (func)=>'function' == typeof func;
const isObject = (obj)=>null !== obj && 'object' == typeof obj;
const isPlainObject = (obj)=>isObject(obj) && '[object Object]' === Object.prototype.toString.call(obj);
async function reduceConfigs({ initial, config, mergeFn = Object.assign }) {
    if (isNil(config)) return initial;
    if (isPlainObject(config)) return isPlainObject(initial) ? mergeFn(initial, config) : config;
    if (isFunction(config)) return await config(initial) ?? initial;
    if (Array.isArray(config)) {
        let result = initial;
        for (const item of config)result = await reduceConfigs({
            initial: result,
            config: item,
            mergeFn
        });
        return result;
    }
    return config ?? initial;
}
async function reduceConfigsWithContext({ initial, config, ctx, mergeFn = Object.assign }) {
    if (isNil(config)) return initial;
    if (isPlainObject(config)) return isPlainObject(initial) ? mergeFn(initial, config) : config;
    if (isFunction(config)) return await config(initial, ctx) ?? initial;
    if (Array.isArray(config)) {
        let result = initial;
        for (const item of config)result = await reduceConfigsWithContext({
            initial: result,
            config: item,
            ctx,
            mergeFn
        });
        return result;
    }
    return config ?? initial;
}
async function reduceConfigsWithMergedContext({ initial, config, ctx, mergeFn = Object.assign }) {
    if (isNil(config)) return initial;
    if (isPlainObject(config)) return isPlainObject(initial) ? mergeFn(initial, config) : config;
    if (isFunction(config)) return await config({
        value: initial,
        ...ctx
    }) ?? initial;
    if (Array.isArray(config)) {
        let result = initial;
        for (const item of config)result = await reduceConfigsWithMergedContext({
            initial: result,
            config: item,
            ctx,
            mergeFn
        });
        return result;
    }
    return config ?? initial;
}
export { reduceConfigs, reduceConfigsWithContext, reduceConfigsWithMergedContext };
