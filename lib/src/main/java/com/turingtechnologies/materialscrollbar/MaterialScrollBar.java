/*
 *  Copyright © 2016-2017, Turing Technologies, an unincorporated organisation of Wynne Plaga
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.turingtechnologies.materialscrollbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;

/*
 * Table Of Contents:
 *
 * I - Initial Setup
 * II - Abstraction for flavour differentiation
 * III - Customisation methods
 * IV - Misc Methods
 *
 * Outline for developers.
 *
 * The two flavours of the MaterialScrollBar are the DragScrollBar and the TouchScrollBar. They both
 * extend this class. Implementations which are unique to each flavour are implemented through
 * abstraction. The use of the T generic is used to maintain the identity of the subclass when
 * chaining settings (ie. So that DragScrollBar(...).setIndicator(...) will return dragScrollBar and
 * not MaterialScrollBar).
 *
 * The class can be instantiated only through XML.
 *
 * Scrolling logic is computed separably in ScrollingUtilities. A unique instance is made for each
 * instance of the bar.
 */
@SuppressWarnings({"unchecked", "unused"})
abstract class MaterialScrollBar<T> extends RelativeLayout implements IScrollBar<T> {

    //Component Views
    private View handleTrack;
    Handle handleThumb;
    Indicator indicator;

    //Characteristics
    int handleColour;
    int handleOffColour = Color.parseColor("#9c9c9c");
    protected boolean hidden = true;
    private int textColour = ContextCompat.getColor(getContext(), android.R.color.white);
    boolean lightOnTouch;
    private TypedArray a; //XML attributes
    private Boolean rtl = false;
    boolean hiddenByUser = false;

    //Associated Objects
    RecyclerView recyclerView;
    private int seekId = 0; //ID of the associated RecyclerView
    ScrollingUtilities scrollUtils = new ScrollingUtilities(this);
    SwipeRefreshLayout swipeRefreshLayout;

    //Misc
    private OnLayoutChangeListener indicatorLayoutListener;
    private boolean enabled = true;


    //CHAPTER I - INITIAL SETUP

    //Programmatic constructor
    MaterialScrollBar(Context context, RecyclerView recyclerView, boolean lightOnTouch){
        super(context);

        this.recyclerView = recyclerView;

        addView(setUpHandleTrack(context)); //Adds the handle track
        addView(setUpHandle(context, lightOnTouch)); //Adds the handle

        setRightToLeft(Utils.isRightToLeft(context)); //Detects and applies the Right-To-Left status of the app

        generalSetup();
    }

    //Style-less XML Constructor
    MaterialScrollBar(Context context, AttributeSet attributeSet){
        this(context, attributeSet, 0);
    }

    //Styled XML Constructor
    MaterialScrollBar(Context context, AttributeSet attributeSet, int defStyle){
        super(context, attributeSet, defStyle);

        setUpProps(context, attributeSet); //Discovers and applies XML attributes

        addView(setUpHandleTrack(context)); //Adds the handle track
        addView(setUpHandle(context, a.getBoolean(R.styleable.MaterialScrollBar_msb_lightOnTouch, true))); //Adds the handle

        a.recycle();

        setRightToLeft(Utils.isRightToLeft(context)); //Detects and applies the Right-To-Left status of the app
    }

    //Unpacks XML attributes and ensures that no mandatory attributes are missing, then applies them.
    void setUpProps(Context context, AttributeSet attrs){
        a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.MaterialScrollBar,
                0, 0);
        if(!a.hasValue(R.styleable.MaterialScrollBar_msb_lightOnTouch)){
            throw new IllegalStateException(
                    "You are missing the following required attributes from a scroll bar in your XML: lightOnTouch");
        }

        if(!isInEditMode()){
            seekId = a.getResourceId(R.styleable.MaterialScrollBar_msb_recyclerView, 0); //Discovers and saves the ID of the recyclerView
        }

        implementPreferences();
    }

    //Sets up bar.
    View setUpHandleTrack(Context context){
        handleTrack = new View(context);
        LayoutParams lp = new RelativeLayout.LayoutParams(Utils.getDP(12, this), LayoutParams.MATCH_PARENT);
        lp.addRule(ALIGN_PARENT_RIGHT);
        handleTrack.setLayoutParams(lp);
        handleTrack.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray));
        ViewCompat.setAlpha(handleTrack, 0.4F);
        return(handleTrack);
    }

    //Sets up handleThumb.
    Handle setUpHandle(Context context, Boolean lightOnTouch){
        handleThumb = new Handle(context, getMode());
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(Utils.getDP(12, this),
                Utils.getDP(72, this));
        lp.addRule(ALIGN_PARENT_RIGHT);
        handleThumb.setLayoutParams(lp);

        this.lightOnTouch = lightOnTouch;
        int colourToSet;
        handleColour = fetchAccentColour(context);
        if(lightOnTouch){
            colourToSet = Color.parseColor("#9c9c9c");
        } else {
            colourToSet = handleColour;
        }
        handleThumb.setBackgroundColor(colourToSet);
        return handleThumb;
    }

    //Implements optional attributes.
    void implementPreferences(){
        if(a.hasValue(R.styleable.MaterialScrollBar_msb_barColour)){
            setBarColour(a.getColor(R.styleable.MaterialScrollBar_msb_barColour, 0));
        }
        if(a.hasValue(R.styleable.MaterialScrollBar_msb_handleColour)){
            setHandleColour(a.getColor(R.styleable.MaterialScrollBar_msb_handleColour, 0));
        }
        if(a.hasValue(R.styleable.MaterialScrollBar_msb_handleOffColour)){
            setHandleOffColour(a.getColor(R.styleable.MaterialScrollBar_msb_handleOffColour, 0));
        }
        if(a.hasValue(R.styleable.MaterialScrollBar_msb_textColour)){
            setTextColour(a.getColor(R.styleable.MaterialScrollBar_msb_textColour, 0));
        }
        if(a.hasValue(R.styleable.MaterialScrollBar_msb_barThickness)){
            setBarThickness(a.getDimensionPixelSize(R.styleable.MaterialScrollBar_msb_barThickness, 0));
        }
        if(a.hasValue(R.styleable.MaterialScrollBar_msb_rightToLeft)){
            setRightToLeft(a.getBoolean(R.styleable.MaterialScrollBar_msb_rightToLeft, false));
        }
        implementFlavourPreferences(a);
    }

    @Override
    public T setRecyclerView(RecyclerView rv){
        if(seekId != 0){
            throw new RuntimeException("There is already a recyclerView set by XML.");
        } else if (recyclerView != null){
            throw new RuntimeException("There is already a recyclerView set.");
        }
        recyclerView = rv;
        generalSetup();
        return (T)this;
    }

    //Waits for all of the views to be attached to the window and then implements general setup.
    //Waiting must occur so that the relevant recyclerview can be found.
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if(seekId != 0){
            recyclerView = (RecyclerView) getRootView().findViewById(seekId);
            generalSetup();
        }
    }

    //General setup.
    private void generalSetup(){
        recyclerView.setVerticalScrollBarEnabled(false); // disable any existing scrollbars
        recyclerView.addOnScrollListener(new scrollListener()); // lets us read when the recyclerView scrolls

        setTouchIntercept(); // catches touches on the bar

        identifySwipeRefreshParents();

        checkCustomScrolling();

        //Hides the view
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_SELF, rtl ? -getHideRatio() : getHideRatio(),
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f);
        anim.setDuration(0);
        anim.setFillAfter(true);
        hidden = true;
        startAnimation(anim);
    }

    //Identifies any SwipeRefreshLayout parent so that it can be disabled and enabled during scrolling.
    void identifySwipeRefreshParents(){
        boolean cycle = true;
        ViewParent parent = getParent();
        if(parent != null){
            while(cycle){
                if(parent instanceof SwipeRefreshLayout){
                    swipeRefreshLayout = (SwipeRefreshLayout)parent;
                    cycle = false;
                } else {
                    if(parent.getParent() == null){
                        cycle = false;
                    } else {
                        parent = parent.getParent();
                    }
                }
            }
        }
    }

    boolean sizeUnchecked = true;

    //Checks each time the bar is laid out. If there are few enough view that
    //they all fit on the screen then the bar is hidden. If a view is added which doesn't fit on
    //the screen then the bar is unhidden.
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (!enabled) {
            return;
        }

        if(recyclerView == null && !isInEditMode()){
            throw new RuntimeException("You need to set a recyclerView for the scroll bar, either in the XML or using setRecyclerView().");
        }

        if(sizeUnchecked && !isInEditMode()){
            scrollUtils.getCurScrollState();
            if(scrollUtils.getAvailableScrollHeight() <= 0){
                handleTrack.setVisibility(GONE);
                handleThumb.setVisibility(GONE);
            } else {
                handleTrack.setVisibility(VISIBLE);
                handleThumb.setVisibility(VISIBLE);
                sizeUnchecked = false;
            }
        }
    }

    // Makes the bar render correctly for XML
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Utils.getDP(12, this);
        int desiredHeight = 100;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(desiredWidth, widthSize);
        } else {
            //Be whatever you want
            width = desiredWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    //CHAPTER II - ABSTRACTION FOR FLAVOUR DIFFERENTIATION

    abstract void setTouchIntercept();

    abstract int getMode();

    abstract float getHideRatio();

    abstract void onScroll();

    abstract boolean getHide();

    abstract void implementFlavourPreferences(TypedArray a);

    abstract float getHandleOffset();

    abstract float getIndicatorOffset();

    //CHAPTER III - CUSTOMISATION METHODS

    private void checkCustomScrollingInterface(){
        if((recyclerView.getAdapter() instanceof  ICustomScroller)){
            scrollUtils.customScroller = (ICustomScroller) recyclerView.getAdapter();
        }
    }

    /**
     * The scrollBar should attempt to use dev provided scrolling logic and not default logic.
     *
     * The adapter must implement {@link ICustomScroller}.
     */
    private void checkCustomScrolling(){
        if (ViewCompat.isAttachedToWindow(this))
            checkCustomScrollingInterface();
        else
            addOnLayoutChangeListener(new OnLayoutChangeListener()
            {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
                {
                    MaterialScrollBar.this.removeOnLayoutChangeListener(this);
                    checkCustomScrollingInterface();
                }
            });
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar handleThumb.
     * @param colour to set the handleThumb.
     */
    @Override
    public T setHandleColour(String colour){
        handleColour = Color.parseColor(colour);
        setHandleColour();
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar handleThumb.
     * @param colour to set the handleThumb.
     */
    @Override
    public T setHandleColour(@ColorInt int colour){
        handleColour = colour;
        setHandleColour();
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar handleThumb.
     * @param colourResId to set the handleThumb.
     */
    @Override
    public T setHandleColourRes(@ColorRes int colourResId){
        handleColour = ContextCompat.getColor(getContext(), colourResId);
        setHandleColour();
        return (T)this;
    }

    private void setHandleColour(){
        if(indicator != null){
            ((GradientDrawable)indicator.getBackground()).setColor(handleColour);
        }
        if(!lightOnTouch){
            handleThumb.setBackgroundColor(handleColour);
        }
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar handleThumb when unpressed. Only applies if lightOnTouch is true.
     * @param colour to set the handleThumb when unpressed.
     */
    @Override
    public T setHandleOffColour(String colour){
        handleOffColour = Color.parseColor(colour);
        if(lightOnTouch){
            handleThumb.setBackgroundColor(handleOffColour);
        }
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar handleThumb when unpressed. Only applies if lightOnTouch is true.
     * @param colour to set the handleThumb when unpressed.
     */
    @Override
    public T setHandleOffColour(@ColorInt int colour){
        handleOffColour = colour;
        if(lightOnTouch){
            handleThumb.setBackgroundColor(handleOffColour);
        }
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar handleThumb when unpressed. Only applies if lightOnTouch is true.
     * @param colourResId to set the handleThumb when unpressed.
     */
    @Override
    public T setHandleOffColourRes(@ColorRes int colourResId){
        handleOffColour = ContextCompat.getColor(getContext(), colourResId);
        if(lightOnTouch){
            handleThumb.setBackgroundColor(handleOffColour);
        }
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar.
     * @param colour to set the bar.
     */
    @Override
    public T setBarColour(String colour){
        handleTrack.setBackgroundColor(Color.parseColor(colour));
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar.
     * @param colour to set the bar.
     */
    @Override
    public T setBarColour(@ColorInt int colour){
        handleTrack.setBackgroundColor(colour);
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the colour of the scrollbar.
     * @param colourResId to set the bar.
     */
    @Override
    public T setBarColourRes(@ColorRes int colourResId){
        handleTrack.setBackgroundColor(ContextCompat.getColor(getContext(), colourResId));
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the text colour of the indicator. Will do nothing if there is no section indicator.
     * @param colour to set the text of the indicator.
     */
    @Override
    public T setTextColour(@ColorInt int colour){
        textColour = colour;
        if(indicator != null){
            indicator.setTextColour(textColour);
        }
        return(T)this;
    }


    /**
     * Provides the ability to programmatically set the text colour of the indicator. Will do nothing if there is no section indicator.
     * @param colourResId to set the text of the indicator.
     */
    @Override
    public T setTextColourRes(@ColorRes int colourResId){
        textColour = ContextCompat.getColor(getContext(), colourResId);
        if(indicator != null){
            indicator.setTextColour(textColour);
        }
        return (T)this;
    }

    /**
     * Provides the ability to programmatically set the text colour of the indicator. Will do nothing if there is no section indicator.
     * @param colour to set the text of the indicator.
     */
    @Override
    public T setTextColour(String colour){
        textColour = Color.parseColor(colour);
        if(indicator != null){
            indicator.setTextColour(textColour);
        }
        return (T)this;
    }

    /**
     * Removes any indicator.
     */
    @Override
    public T removeIndicator(){
        if(this.indicator != null){
            this.indicator.removeAllViews();
        }
        this.indicator = null;
        return (T)this;
    }

    /**
     * Adds an indicator which accompanies this scroll bar.
     *
     * @param addSpaceSide Should space be put between the indicator and the bar or should they touch?
     */
    @Override
    public T setIndicator(final Indicator indicator, final boolean addSpaceSide) {
        if(ViewCompat.isAttachedToWindow(this)){
            setupIndicator(indicator, addSpaceSide);
        } else {
            removeOnLayoutChangeListener(indicatorLayoutListener);
            indicatorLayoutListener = new OnLayoutChangeListener()
            {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom){
                    setupIndicator(indicator, addSpaceSide);
                    MaterialScrollBar.this.removeOnLayoutChangeListener(this);
                }
            };
            addOnLayoutChangeListener(indicatorLayoutListener);
        }
        return (T)this;
    }

    /**
     * Shared code for the above method.
     */
    private void setupIndicator(Indicator indicator, boolean addSpaceSide){
        MaterialScrollBar.this.indicator = indicator;
        indicator.testAdapter(recyclerView.getAdapter());
        indicator.setRTL(rtl);
        indicator.linkToScrollBar(MaterialScrollBar.this, addSpaceSide);
        indicator.setTextColour(textColour);
    }

    /**
     * Allows the developer to set a custom bar thickness.
     * @param thickness The desired bar thickness.
     */
    public T setBarThickness(int thickness){
        LayoutParams layoutParams = (LayoutParams) handleThumb.getLayoutParams();
        layoutParams.width = thickness;
        handleThumb.setLayoutParams(layoutParams);

        layoutParams = (LayoutParams) handleTrack.getLayoutParams();
        layoutParams.width = thickness;
        handleTrack.setLayoutParams(layoutParams);

        if(indicator != null){
            indicator.setSizeCustom(thickness);
        }

        layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.width = thickness;
        setLayoutParams(layoutParams);

        return (T)this;
    }

    /**
     * Hide or unhide the scrollBar.
     */
    public void setScrollBarHidden(boolean hidden){
        hiddenByUser = hidden;
        if(hiddenByUser){
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    /**
     * Overrides the right-to-left settings for the scroll bar.
     */
    public void setRightToLeft(boolean rtl){
        this.rtl = rtl;
        handleThumb.setRightToLeft(rtl);
        if(indicator != null){
            indicator.setRTL(rtl);
            indicator.setLayoutParams(indicator.refreshMargins((LayoutParams) indicator.getLayoutParams()));
        }
    }

    //CHAPTER IV - MISC METHODS

    //Fetch accent colour on devices running Lollipop or newer.
    static int fetchAccentColour(Context context) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorAccent });
        int color = a.getColor(0, 0);

        a.recycle();

        return color;
    }

    /**
     * Animates the bar out of view
     */
    void fadeOut(){
        if(!hidden){
            TranslateAnimation anim = new TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_SELF, rtl ? -getHideRatio() : getHideRatio(),
                    Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f);
            anim.setDuration(150);
            anim.setFillAfter(true);
            hidden = true;
            startAnimation(anim);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    handleThumb.expandHandle();
                }
            }, anim.getDuration() / 3);
        }
    }

    /**
     * Animates the bar into view
     */
    void fadeIn(){
        if(hidden && getHide() && !hiddenByUser){
            hidden = false;
            TranslateAnimation anim = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, rtl ? -getHideRatio() : getHideRatio(),
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f);
            anim.setDuration(150);
            anim.setFillAfter(true);
            startAnimation(anim);
            handleThumb.collapseHandle();
        }
    }

    /**
     * Enables or disables this scrollbar completely
     *
     * A disabled scrollbar must not be attached to a RecyclerView and does not draw anything
     * ATTENTION: Currently, this MUST be called before the view is drawn!
     */
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected void onDown(MotionEvent event){
        if (indicator != null && indicator.getVisibility() == INVISIBLE && recyclerView.getAdapter() != null) {
            indicator.setVisibility(VISIBLE);
            if(Build.VERSION.SDK_INT >= 12){
                indicator.setAlpha(0F);
                indicator.animate().alpha(1F).setDuration(150).setListener(new AnimatorListenerAdapter() {
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);

                        indicator.setAlpha(1F);
                    }
                });
            }
        }

        int top = handleThumb.getHeight() / 2;
        int bottom = recyclerView.getHeight() - Utils.getDP(72, recyclerView.getContext());
        float boundedY = Math.max(top, Math.min(bottom, event.getY() - getHandleOffset()));
        scrollUtils.scrollToPositionAtProgress((boundedY - top) / (bottom - top));
        scrollUtils.scrollHandleAndIndicator();
        recyclerView.onScrolled(0, 0);

        if (lightOnTouch) {
            handleThumb.setBackgroundColor(handleColour);
        }
    }

    protected void onUp(){
        if (indicator != null && indicator.getVisibility() == VISIBLE) {
            if (Build.VERSION.SDK_INT <= 12) {
                indicator.clearAnimation();
            }
            if(Build.VERSION.SDK_INT >= 12){
                indicator.animate().alpha(0F).setDuration(150).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);

                        indicator.setVisibility(INVISIBLE);
                    }
                });
            } else {
                indicator.setVisibility(INVISIBLE);
            }
        }

        if (lightOnTouch) {
            handleThumb.setBackgroundColor(handleOffColour);
        }
    }

    class scrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            scrollUtils.scrollHandleAndIndicator();
            if(dy != 0){
                onScroll();
            }

            //Disables any swipeRefreshLayout parent if the recyclerview is not at the top and enables it if it is.
            if(swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()){
                if(((LinearLayoutManager)recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition() == 0){
                    swipeRefreshLayout.setEnabled(true);
                } else {
                    swipeRefreshLayout.setEnabled(false);
                }
            }
        }
    }

}