type OneOrMany<T> = T | T[];
type MaybePromise<T> = T | Promise<T>;
export type ConfigChain<T> = OneOrMany<T | ((config: T) => MaybePromise<T | void>)>;
export type ConfigChainWithContext<T, Ctx> = OneOrMany<T | ((config: T, ctx: Ctx) => MaybePromise<T | void>)>;
export type ConfigChainWithMergedContext<T, Ctx> = OneOrMany<T | ((merged: {
    value: T;
} & Ctx) => MaybePromise<T | void>)>;
/**
 * Merge one or more configs into a final config,
 * and allow modifying the config object via a function that may be async.
 */
export declare function reduceConfigs<T>({ initial, config, mergeFn, }: {
    /**
     * Initial configuration object.
     */
    initial: T;
    /**
     * The configuration object, function, or array of configuration objects/functions
     * to be merged into the initial configuration.
     */
    config?: ConfigChain<T> | undefined;
    /**
     * The function used to merge configuration objects.
     * @default Object.assign
     */
    mergeFn?: typeof Object.assign;
}): Promise<T>;
/**
 * Merge one or more configs into a final config,
 * and allow modifying the config object via a function that may be async and accepts a context object.
 */
export declare function reduceConfigsWithContext<T, Ctx>({ initial, config, ctx, mergeFn, }: {
    /**
     * Initial configuration object.
     */
    initial: T;
    /**
     * The configuration object, function, or array of configuration objects/functions
     * to be merged into the initial configuration.
     */
    config?: ConfigChainWithContext<T, Ctx> | undefined;
    /**
     * Context object that can be used within the configuration functions.
     */
    ctx?: Ctx;
    /**
     * The function used to merge configuration objects.
     * @default Object.assign
     */
    mergeFn?: typeof Object.assign;
}): Promise<T>;
/**
 * Merge one or more configs into a final config,
 * and allow modifying the config object via a function that may be async and accepts a merged context object.
 */
export declare function reduceConfigsWithMergedContext<T, Ctx>({ initial, config, ctx, mergeFn, }: {
    /**
     * Initial configuration object.
     */
    initial: T;
    /**
     * The configuration object, function, or array of configuration objects/functions
     * to be merged into the initial configuration.
     */
    config?: ConfigChainWithMergedContext<T, Ctx> | undefined;
    /**
     * Context object that will be merged with the current value and passed to configuration functions.
     */
    ctx?: Ctx;
    /**
     * The function used to merge configuration objects.
     * @default Object.assign
     */
    mergeFn?: typeof Object.assign;
}): Promise<T>;
export {};
